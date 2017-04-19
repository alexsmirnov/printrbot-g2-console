package alexsmirnov.pbconsole

import org.scalatest.fixture.FlatSpec
import alexsmirnov.pbconsole.serial.PortStub
import org.scalatest.concurrent.Eventually
import scala.concurrent.{ Future, Await, ExecutionContext, duration }
import java.util.concurrent.CopyOnWriteArrayList
import alexsmirnov.pbconsole.serial.Port
import org.scalatest.time.SpanSugar.GrainOfTime
import org.scalatest.concurrent.TimeLimits
import org.scalatest.time.SpanSugar.GrainOfTime
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.concurrent.ThreadSignaler
import java.util.concurrent.Semaphore

class PrinterTest extends FlatSpec with Eventually with TimeLimitedTests {
  import ExecutionContext.Implicits._
  import duration._
  type FixtureParam = Printer
  val timeLimit = new GrainOfTime(2).seconds

  override val defaultTestSignaler = ThreadSignaler

  var semaphore: Semaphore = _

  val GCmd = """^G(\d+).*""".r
  val MCmd = """^M(\d+).*""".r
  def smoothie(line: String): String = {
    println("receive: "+line)
    semaphore.acquire()
    line match {
      case GCmd(_) => "ok"
      case MCmd("105") => "ok T:20.0 20.0 @0 B:20.0 30.0 @255"
      case MCmd("114") => "ok C: X100 Y100 Z100 E0"
      case MCmd(_) => "ok"
      case "{sr:{}}" => "{r:{}}"
      case other => "unknown command"
    }
  }
  def withFixture(test: OneArgTest) = {
    semaphore = new Semaphore(0)
    withFixture(test.toNoArgTest(new Printer(new PortStub(smoothie _,"Smoothie"), SmoothieResponse(_)))) // "loan" the fixture to the test
  }

  def startAndWait(p: Printer) = {
    @volatile
    var connected = false
    p.addReceiveListener{ (src,resp) => println(s"Received $resp to request from $src") }
    p.addStateListener {
      case Port.Connected(_, _) => connected = true
      case Port.Disconnected => connected = false
    }
    p.start()
    eventually {
      assert(connected === true)
    }
  }
  
  val dataStream = Stream.from(1).map { n => GCommand(s"G0 X$n Y$n", Source.Console) }
  val commandStream = Stream.from(1).map { n => "{sr:{}}" }
  
  "Printer" should "set connected on connect" in { p =>
    startAndWait(p)
  }
  it should "send data with back pressure" in { p =>
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
  
  it should "send commands immediately" in { p =>
    startAndWait(p)
    semaphore.release(105)
    val data = Future(Stream.from(1).map { n => GCommand(s"X$n Y$n", Source.Console) }.take(50).foreach(p.sendData _) )
    val start = System.currentTimeMillis()
    commandStream.take(50).foreach(p.sendLine)
    assert((System.currentTimeMillis() - start) < 20)
  }
  
  it should "receive responses" in { p =>
    val received = new CopyOnWriteArrayList[String]
    p.addReceiveListener { (s, r) => received.add(r.rawLine) }
    semaphore.release(105)
    startAndWait(p)
    val data = Future(dataStream.take(60).foreach(p.sendData))
    eventually(timeout(timeLimit))(assert(received.size >= 60))
  }
}