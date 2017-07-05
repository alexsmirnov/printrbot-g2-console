package alexsmirnov.pbconsole.serial

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

/**
 * @author asmirnov
 *
 */
object Port {
  val LOG = Logger.getLogger("alexsmirnov.pbconsole.serial.Port")
  def threadFactory(name: String,daemon: Boolean=true) = new ThreadFactory {
        val count = new AtomicInteger
        def newThread(r: Runnable) = {
          val t = new Thread(r)
          t.setName(name+count.incrementAndGet())
          t.setDaemon(daemon)
          t
        }
  }
  val scheduler =
    Executors.newScheduledThreadPool(10,threadFactory("scheduler-",true))
  implicit val executor = ExecutionContext.fromExecutorService(scheduler)
  sealed trait StateEvent
  case class Connected(name: String, baud: Int) extends StateEvent
  case object Disconnected extends StateEvent
  def apply(port: Regex, baud: Int = 115200): Port = new NRJavaSerialPort(port, baud)
}

/**
 * Base functionality for serial communications with printer.
 * Producer emits received bytes. No buffering for received data, back pressure ignored.
 * Consumer sends bytes to serial output.
 * Port supposed for auto connect /reconnect, so meaning of cancel/complete changed:
 * cancel from consumer means disconnect. producer should clear all states, but keep subscribtion for next reconnect.
 * onComplete from producer also means port disconnect. It will continue to send data if port reconnected.
 * @author asmirnov
 *
 */
trait Port extends Publisher[Byte] with Subscriber[Byte] with Closeable {
  def addStateListener(listener: Port.StateEvent => Unit): Unit
  def run(): Unit
  def disconnect(reconnect: Boolean = true): Unit
}