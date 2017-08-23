package alexsmirnov.pbconsole.serial

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.fixture.FlatSpec
import org.scalatest.time.SpanSugar._

import alexsmirnov.pbconsole.CommandSource
import alexsmirnov.pbconsole.gcode.GCode
import alexsmirnov.pbconsole.gcode.Request
import alexsmirnov.pbconsole.gcode.Response
import alexsmirnov.pbconsole.gcode.SmoothieResponse

class PrinterTest extends FlatSpec with Eventually with TimeLimitedTests with Matchers {
  import scala.concurrent.ExecutionContext.Implicits._
  type FixtureParam = (Semaphore, Printer)
  val timeLimit = 2.seconds

  override val defaultTestSignaler = ThreadSignaler
  
  val LINEMODE_SIZE = 4
  
  val QUEUE_SIZE = 5

  def withFixture(test: OneArgTest) = {

    val semaphore = new Semaphore(0)
    val port = new PortStub({ line: String =>
      semaphore.acquire()
      println(line)
      line match {
        case Request.GCmd(n) => Seq("ok X:" + n)
        case Request.MCmd("105") => Seq("ok T:20.0 /20.0 @0 B:20.0 /30.0 @255")
        case Request.MCmd("114") => Seq("ok C: X100 Y100 Z100 E0")
        case Request.MCmd(n) => Seq("ok Y:"+n)
        case "{sr:{}}" => Seq("{r:{}}")
        case other => Seq("unknown command")
      }
    }, "Smoothie")
    val printer: Printer = new PrinterImpl(port,SmoothieResponse(_)) // new PrinterImpl(port, SmoothieResponse(_))
    try {
      withFixture(test.toNoArgTest(semaphore -> printer)) // "loan" the fixture to the test
    } finally {
      printer.stop()
    }
  }

  def startAndWait(p: Printer) = {
    @volatile
    var connected = false
    val received = new CopyOnWriteArrayList[(Response,CommandSource)]
    p.addReceiveListener { (s, r) => received.add(s -> r) }
    p.addStateListener {
      case Port.Connected(_, _) => connected = true
      case Port.Disconnected => connected = false
    }
    p.start()
    eventually {
      assert(connected === true)
    }
    received
  }
  def cmd(lines: String *) = {_: Printer.Positioning => lines.iterator.map(GCode(_))}

  val dataStream = Stream.from(1).map { n => GCode(s"G$n X$n Y$n") }
  val commandStream = Stream.from(1).map { n => "{sr:{}}" }
  
  def print( fp: (Semaphore,Printer), size: Int ) = {
    val (semaphore, p) = fp
    val received = startAndWait(p)
    Future(dataStream.take(size).foreach(p.sendData(_,CommandSource.Job)))
    // Simulate slow printing
    Future{ 0 until (size + QUEUE_SIZE) foreach { _ => semaphore.release(); Thread.sleep(1)} } 
    received
  }

  "Printer" should "set connected on connect" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
  }
  
  "offerCommand" should "send commands to printer" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    assert(p.offerCommands(cmd("G2"), CommandSource.Monitor),"cannot offer command")
    eventually{ assert(semaphore.hasQueuedThreads) }
  }
  
  it should "assign source to response" in { fp =>
    val (semaphore, p) = fp
    val received = startAndWait(p)
    assert(p.offerCommands(cmd("G2"), CommandSource.Monitor),"cannot offer command")
    semaphore.release(2)
    eventually{ received.map{ case (r,s) => (r.rawLine,s) } should contain("ok X:2"->CommandSource.Monitor) }
  }
  
  it should "reject commands if queue is full" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    val sent = Seq.fill(QUEUE_SIZE*2)(cmd("G3")).takeWhile(p.offerCommands(_, CommandSource.Console)).size
    sent should be (QUEUE_SIZE)
  }

  it should "send all commands in batch together while printing" in { fp =>
    val received = print(fp,50)
    Thread.sleep(10)
    assert(fp._2.offerCommands(cmd("M2","M3","M4","M5"), CommandSource.Monitor),"cannot offer command")
    eventually { received.map{_._1.rawLine}.indexOfSlice(List("ok Y:2","ok Y:3","ok Y:4","ok Y:5")) should ( be >(1) and be <(49-4) ) }
  }

  it should "reject commands if printer disconnected" in { fp =>
    val (semaphore, p) = fp
    assert(!p.offerCommands(cmd("G2"), CommandSource.Monitor),"offered command to disconnected printer")
  }

  "query" should "send command to printer" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    p.query(GCode("M2"), CommandSource.Monitor)
    eventually{ assert(semaphore.hasQueuedThreads) }
  }
  
  it should "reject command if queue is full" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    // Failed future has defined value
    val sent = Seq.fill(QUEUE_SIZE*2)(GCode("G3")).takeWhile( p.query(_, CommandSource.Console).value.isEmpty).size
    sent should be (QUEUE_SIZE)
  }

  it should "complete future on receive response" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    val f = p.query(GCode("M105"), CommandSource.Monitor)
    semaphore.release()
    eventually(assert(f.isCompleted,"future not completed"))
    f.value should not be empty
    f.value.get shouldBe a[Success[_]]
    f.value.get.get.map(_.rawLine) should contain only("ok T:20.0 /20.0 @0 B:20.0 /30.0 @255")
  }

  it should "reject command if printer disconnected" in { fp =>
    val (semaphore, p) = fp
    val f = p.query(GCode("M105"), CommandSource.Monitor)
    assert(f.isCompleted,"future not completed")
    f.value should not be empty
    f.value.get shouldBe a[Failure[_]]
  }
  
  it should "receive response while printing" in { fp =>
    val (semaphore, p) = fp
    val received = print(fp,100)
    val f = p.query(GCode("M105"), CommandSource.Monitor)
    eventually(assert(f.isCompleted,"future not completed"))
    f.value should not be empty
    f.value.get shouldBe a[Success[_]]
    f.value.get.get.map(_.rawLine) should contain only("ok T:20.0 /20.0 @0 B:20.0 /30.0 @255")
    received.size() should be <(100)
  }

  "print" should "send data to printer" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    p.sendData(GCode("M2"), CommandSource.Monitor)
    eventually{ assert(semaphore.hasQueuedThreads) }
  }
  
  it should "block until printer receive response" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    val f = Future(dataStream.take(100).foreach(p.sendData(_,CommandSource.Job)))
    semaphore.release(50)
    Thread.sleep(20)
    f should not be('completed)
    semaphore.release(51)
    eventually{ f should be('completed) }
  }

  it should "return false if printer disconnected" in { fp =>
    pending
    val (semaphore, p) = fp
    startAndWait(p)
    val f = Future(dataStream.take(100).takeWhile(p.sendData(_, CommandSource.Job)).size)
    semaphore.release(10)
    p.stop()
    semaphore.release(100)
    eventually(assert(f.isCompleted,"future not completed"))
    f.value.get.get should be (10 +- 2)
  }

  it should "receive all responses in sent order" in { fp =>
    val (semaphore, p) = fp
    val received = print(fp,10)
    Thread.sleep(150)
    eventually{
     received.map(_._1.rawLine).tail should contain theSameElementsInOrderAs(1 to 10 map{ n => s"ok X:$n"})
    }
  }
  
  it should "assign Job source to response" in { fp =>
    val (semaphore, p) = fp
    val received = print(fp,10)
    eventually{ received.map(_._2).tail should ( have size 10 and contain only(CommandSource.Job) ) }
  }
  
}