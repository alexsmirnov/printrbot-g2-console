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

class PrinterTest extends FlatSpec with Eventually with TimeLimitedTests {
  import ExecutionContext.Implicits._
  import duration._
  type FixtureParam = Printer
  val timeLimit = new GrainOfTime(2).seconds

override val defaultTestSignaler = ThreadSignaler
  def withFixture(test: OneArgTest) = {
      withFixture(test.toNoArgTest(new Printer(new PortStub(),G2Response(_)))) // "loan" the fixture to the test
  }

  def startAndWait(p: Printer) = {
    @volatile
    var connected = false
    p.addStateListener {
      case Port.Connected(_, _) => connected = true
      case Port.Disconnected => connected = false
    }
    p.start()
    eventually {
      assert(connected === true)
    }
  }
  val dataStream = Stream.from(1).map { n => GCommand(s"G0 X$n Y$n",Source.Console) }
  val commandStream = Stream.from(1).map { n => s"{sr:{}}" }
  "Printer" should "set connected on connect" in { p =>
    startAndWait(p)
  }
  it should "send data with back pressure" in { p =>
    startAndWait(p)
    val start = System.currentTimeMillis()
    dataStream.take(99).foreach(p.sendData)
    assert((System.currentTimeMillis() - start) > 50)
  }
  it should "send commands immediately" in { p =>
    startAndWait(p)
    val data = Future(dataStream.take(50).foreach(p.sendData))
    val start = System.currentTimeMillis()
    commandStream.take(50).foreach(p.sendLine)
    assert((System.currentTimeMillis() - start) < 20)
    Await.ready(data, 200.milli)
    assert((System.currentTimeMillis() - start) > 50)
  }
  it should "receive responses" in { p =>
    val received = new CopyOnWriteArrayList[String]
    p.addReceiveListener{ (s,r) => received.add(r.rawLine)}
    startAndWait(p)
    val data = Future(dataStream.take(100).foreach(p.sendData))
    eventually(received.size === 100)
  }
}