package alexsmirnov.pbconsole

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import org.scalatest.Finders
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.fixture.FlatSpec
import org.scalatest.time.SpanSugar._

import alexsmirnov.pbconsole.serial.Port
import alexsmirnov.pbconsole.serial.PortStub
import scala.concurrent.Promise
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class PrinterTest extends FlatSpec with Eventually with TimeLimitedTests {
  import ExecutionContext.Implicits._
  type FixtureParam = (Semaphore, Printer)
  val timeLimit = 2.seconds

  override val defaultTestSignaler = ThreadSignaler

  def withFixture(test: OneArgTest) = {

    val semaphore = new Semaphore(0)
    val printer = new Printer(new PortStub({ line: String =>
      semaphore.acquire()
      line match {
        case Request.GCmd(n) => Seq("ok " + n)
        case Request.MCmd("105") => Seq("ok T:20.0 /20.0 @0 B:20.0 /30.0 @255")
        case Request.MCmd("114") => Seq("ok C: X100 Y100 Z100 E0")
        case Request.MCmd(_) => Seq("ok")
        case "{sr:{}}" => Seq("{r:{}}")
        case other => Seq("unknown command")
      }
    }, "Smoothie"), SmoothieResponse(_))
    try {
      withFixture(test.toNoArgTest(semaphore -> printer)) // "loan" the fixture to the test
    } finally {
      printer.stop()
    }
  }

  def startAndWait(p: Printer) = {
    @volatile
    var connected = false
    val received = new CopyOnWriteArrayList[(CommandSource,Response)]
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

  val dataStream = Stream.from(1).map { n => GCommand(s"G$n X$n Y$n", CommandSource.Console) }
  val commandStream = Stream.from(1).map { n => "{sr:{}}" }

  "Printer" should "set connected on connect" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
  }
  
  it should "send data with back pressure" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    Future(dataStream.take(20).foreach(p.sendData))
    Thread.sleep(150)
    assert(p.commandsStack.size === 4)
    semaphore.release(5)
    Thread.sleep(150)
    assert(p.commandsStack.size === 4)
    semaphore.release(13)
    Thread.sleep(150)
    assert(p.commandsStack.size === 2)
    semaphore.release(10)
    Thread.sleep(150)
    assert(p.commandsStack.size === 0)
  }

  it should "send commands immediately" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    semaphore.release(105)
    val data = Future(Stream.from(1).map { n => GCommand(s"G1X$n Y$n", CommandSource.Console) }.take(50).foreach(p.sendData _))
    val start = System.currentTimeMillis()
    commandStream.take(50).foreach(p.sendLine)
    assert((System.currentTimeMillis() - start) < 20)
  }

  it should "receive all responses in sent order" in { fp =>
    val (semaphore, p) = fp
    val received = startAndWait(p)
    semaphore.release(105)
    val data = Future(dataStream.take(100).foreach(p.sendData))
    eventually(timeout(timeLimit))(assert(received.size >= 101))
    received.zipWithIndex.tail.foreach { case ((s,r),i) => assert(r.rawLine === s"ok $i") }
  }
  
  it should "assign source to response" in { fp =>
    val (semaphore, p) = fp
    val received = startAndWait(p)
    val sources = Seq[CommandSource](CommandSource.Console,CommandSource.Job,CommandSource.Monitor,CommandSource.Job,CommandSource.Monitor)
    semaphore.release(10)
    val data = Future(sources.map(Request("M1",_)).foreach(p.sendData))
    eventually(timeout(timeLimit))(assert(received.size > sources.size))
    received.tail.zip(sources).foreach { case ((s,r),es) => assert(s === es) }
  }
  it should "receive temperature in callback" in { fp =>
    val (semaphore, p) = fp
    semaphore.release(10)
    val received = startAndWait(p)
    val responsePromise = Promise[List[ResponseValue]]()
    p.sendData(QueryCommand("M105",CommandSource.Monitor,{
      case sr: StatusResponse => responsePromise.success(sr.values)
      case other => responsePromise.failure(new Throwable(s"unexpected response $other"))
    }))
    val result = Await.result(responsePromise.future, Duration(2,"sec"))
    println(result)
    assert(result.exists { _ == ExtruderTemp(20.0f) })
  }
  it should "receive temperature during print activity" in { fp =>
    val (semaphore, p) = fp
    semaphore.release(10)
    val received = startAndWait(p)
    val data = Future(Stream.from(1).map { n => GCommand(s"G1X$n Y$n", CommandSource.Job) }.foreach{ c=> p.sendData(c);semaphore.release(1)})
    val responsePromise = Promise[List[ResponseValue]]()
    p.sendData(QueryCommand("M105",CommandSource.Monitor,{
      case sr: StatusResponse => responsePromise.success(sr.values)
      case other => responsePromise.failure(new Throwable(s"unexpected response $other"))
    }))
    val result = Await.result(responsePromise.future, Duration(2,"sec"))
    println(result)
    assert(result.exists { _ == ExtruderTemp(20.0f) })
  }
}