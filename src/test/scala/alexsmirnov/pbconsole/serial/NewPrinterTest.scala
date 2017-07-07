package alexsmirnov.pbconsole.serial

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ThreadSignaler
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.fixture.FlatSpec
import org.scalatest.time.SpanSugar._

import alexsmirnov.pbconsole.CommandSource
import alexsmirnov.pbconsole.gcode.ExtruderTemp
import alexsmirnov.pbconsole.gcode.GCommand
import alexsmirnov.pbconsole.gcode.QueryCommand
import alexsmirnov.pbconsole.gcode.Request
import alexsmirnov.pbconsole.gcode.Response
import alexsmirnov.pbconsole.gcode.ResponseValue
import alexsmirnov.pbconsole.gcode.SmoothieResponse
import alexsmirnov.pbconsole.gcode.StatusResponse
import alexsmirnov.pbconsole.gcode.GCode

class NewPrinterTest extends FlatSpec with Eventually with TimeLimitedTests {
  import scala.concurrent.ExecutionContext.Implicits._
  type FixtureParam = (Semaphore, Printer)
  val timeLimit = 2.seconds

  override val defaultTestSignaler = ThreadSignaler
  
  val LINEMODE_SIZE = 4
  
  val QUEUE_SIZE = 4

  def withFixture(test: OneArgTest) = {

    val semaphore = new Semaphore(0)
    val port = new PortStub({ line: String =>
      semaphore.acquire()
      line match {
        case Request.GCmd(n) => Seq("ok " + n)
        case Request.MCmd("105") => Seq("ok T:20.0 /20.0 @0 B:20.0 /30.0 @255")
        case Request.MCmd("114") => Seq("ok C: X100 Y100 Z100 E0")
        case Request.MCmd(_) => Seq("ok")
        case "{sr:{}}" => Seq("{r:{}}")
        case other => Seq("unknown command")
      }
    }, "Smoothie")
    val printer: Printer = null // new PrinterImpl(port, SmoothieResponse(_))
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

  val dataStream = Stream.from(1).map { n => GCode(s"G$n X$n Y$n") }
  val commandStream = Stream.from(1).map { n => "{sr:{}}" }

  "Printer" should "set connected on connect" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
  }
  
  it should "send commands with back pressure" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
  }

  it should "send data with back pressure" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
  }
  
  it should "send commands between data lines" in { fp =>
    val (semaphore, p) = fp
    startAndWait(p)
    semaphore.release(105)
  }

  it should "receive all responses in sent order" in { fp =>
    val (semaphore, p) = fp
    val received = startAndWait(p)
    semaphore.release(105)
  }
  
  it should "assign source to response" in { fp =>
    val (semaphore, p) = fp
    val received = startAndWait(p)
  }
  
  it should "receive temperature in query result" in { fp =>
    val (semaphore, p) = fp
    semaphore.release(10)
  }
  
  it should "receive temperature during print activity" in { fp =>
    val (semaphore, p) = fp
    semaphore.release(10)
  }
}