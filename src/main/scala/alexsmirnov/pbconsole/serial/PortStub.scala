package alexsmirnov.pbconsole.serial

import alexsmirnov.stream.ReactiveOps._
import org.reactivestreams._
import java.util.concurrent.TimeUnit
import scala.util.Random

object PortStub {
  def g2(line: String) = Seq("""{"r":{"foo":"bar"},"f":[1,0,8]}""")
  val g2welcome = """{"r":{"sr":{"state":"READY"}},"f":[1,0,8]}"""
  val GCmd = """^[Gg](\d+).*""".r
  val MCmd = """^[Mm](\d+).*""".r
  val SetTempCmd = """^[Mm]104\s*S(\d+)""".r
  val SetBedTempCmd = """^[Mm]140\s*S(\d+)""".r
  @volatile var extrudertemp: Float = 20.0f
  @volatile var extruderTarget: Float = 20.0f
  def extruderOut = if (extrudertemp < extruderTarget) 255 else 0
  @volatile var bedtemp: Float = 20.0f
  @volatile var bedTarget: Float = 20.0f
  def bedOut = if (bedtemp < bedTarget) 255 else 0
  lazy val heater = Port.scheduler.scheduleAtFixedRate(new Runnable {
    def run() {
      if (Math.abs(extruderTarget - extrudertemp) > 10.0) {
        extrudertemp += (extruderTarget - extrudertemp) / 10.0f
      } else {
        extrudertemp = extruderTarget + Random.nextFloat()
      }
      if (Math.abs(bedTarget - bedtemp) > 10.0) {
        bedtemp += (bedTarget - bedtemp) / 10.0f
      } else {
        bedtemp = bedTarget + Random.nextFloat()
      }
    }
  }, 100, 100, TimeUnit.MILLISECONDS)
  def smoothie(line: String) = {
    line match {
      case GCmd(n) => Seq("ok")
      case SetTempCmd(t) =>
        if (!heater.isCancelled) {
          extruderTarget = t.toFloat
        }
        Seq("ok")
      case SetBedTempCmd(t) =>
        if (!heater.isCancelled) {
          bedTarget = t.toFloat
        }
        Seq("ok")
      case MCmd("105") =>
        Seq(s"ok T:$extrudertemp /$extruderTarget @$extruderOut B:$bedtemp /$bedTarget @$bedOut")
      case MCmd("114") => Seq("ok C: X100 Y100 Z100 E0")
      case MCmd(_) => Seq("ok")
      case "{sr:{}}" => Seq("{r:{}}")
      case other => Seq("unknown command")
    }
  }
}

class PortStub(tr: String => Seq[String] = PortStub.smoothie, welcome: => String = "Smoothie") extends Port {

  val receiver = toLines
  val responser = linesToBytes
  receiver.async(100).flatMap(tr).subscribe(responser)

  var listeners: List[Port.StateEvent => Unit] = Nil

  def addStateListener(listener: Port.StateEvent => Unit): Unit = {
    listeners = listener +: listeners
  }

  def run(): Unit = {
    Port.scheduler.schedule(new Runnable {
      def run() {
        val ev = Port.Connected("foo", 0)
        listeners.foreach(_(ev))
        receiver.request(100L)
        responser.request(100L)
        responser.requestProducer(100)
        responser.onNext(welcome)
      }
    }, 10, TimeUnit.MILLISECONDS)
  }

  def close(): Unit = {
  }

  def onComplete(): Unit = {
    receiver.onComplete()
  }

  def onError(t: Throwable): Unit = {
    receiver.onError(t)
  }

  def onNext(b: Byte): Unit = {
    receiver.onNext(b)
  }

  def onSubscribe(sub: Subscription): Unit = {
    receiver.onSubscribe(sub)
  }

  def subscribe(s: Subscriber[_ >: Byte]): Unit = {
    responser.subscribe(s)
  }
}