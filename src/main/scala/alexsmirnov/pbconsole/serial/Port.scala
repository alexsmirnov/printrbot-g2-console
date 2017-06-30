package alexsmirnov.pbconsole.serial

import scala.collection.{ mutable => m }
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.io.Closeable
import scala.util.matching.Regex
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import gnu.io.NRSerialPort
import java.util.concurrent.Semaphore
import java.io.IOException
import java.util.logging.Logger
import scala.util.{ Try, Success, Failure }
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.LockSupport
import gnu.io.SerialPortEventListener
import gnu.io.SerialPortEvent
import java.util.concurrent.atomic.AtomicInteger

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
 * @author asmirnov
 *
 */
class NRJavaSerialPort(port: Regex, baud: Int = 115200) extends Port {
  import Port._
  val listeners = m.Buffer[(Port.StateEvent => Unit)]()
  val mutex = new Object
  def addStateListener(listener: Port.StateEvent => Unit) = listeners += listener

  @volatile
  private[this] var connection: Option[(NRSerialPort,Thread)] = None

  def run() {
    require(null != subscription, "Subscription required to run serial port")
    require(null != inputSubscribtion, "Subscriber required to run serial port")
    waitForConnect
  }
  
  def close() = {
    disconnect(false)
  }

  def waitForConnect = {
    val f = Future {
      var activePort: Option[String] = None
      do {
        Thread.sleep(1000L)
        val ports = NRSerialPort.getAvailableSerialPorts.iterator()
        while (ports.hasNext() && activePort.isEmpty) {
          val portName = ports.next
          LOG.info(s"find serial port $portName")
          if (port.findFirstIn(portName).isDefined) {
            LOG.info(s"Port $portName matches expected $port")
            activePort = Some(portName)
          }
        }
      } while (activePort.isEmpty)
      new NRSerialPort(activePort.get, baud)
    }
    f.onComplete {
      case Success(sr) => connect(sr)
      case Failure(err) => LOG.severe(s"Error in port watch process ${err.getMessage}")
    }
  }

  def connect(sr: NRSerialPort) {
    mutex.synchronized {
      if (sr.connect()) {
        LOG.info(s"Connected to $sr")
        sr.notifyOnDataAvailable(true)
        val event = Connected(sr.getSerialPortInstance.getName, sr.getBaud)
        listeners.foreach { _(event) }
        val buffSize = sr.getSerialPortInstance.getOutputBufferSize
        subscription.request(if(buffSize > 0) buffSize else 1024)
        val inThread = startReceiver(sr, inputSubscribtion)
        connection = Some((sr,inThread))
      } else {
        LOG.severe("failed to connect serial port")
        waitForConnect
      }
    }
  }

  def disconnect(reconnect: Boolean = true) {
    // TODO - stop read thread before disconnect
    LOG.info(s"Disconnect  $connection")
    mutex.synchronized {
      connection.foreach { case(p,t) =>
        connection = None
        if(Thread.currentThread() != t) t.interrupt()
        inputSubscribtion.onDisconnect
        subscription.cancel()
        listeners.foreach { _(Disconnected) }
        p.disconnect()
        if (reconnect) waitForConnect
      }
    }
  }

  def startReceiver(sr: NRSerialPort, sub: ReceiverSubscription) = {
    val t = new Thread(new Runnable {
      def run() {
        try {
          while (!Thread.interrupted() && sr.isConnected() ) {
            val lastByte = sr.getInputStream.read()
            if (lastByte >= 0) {
              sub.next(lastByte)
            } else {
              // EOF - no data to read. wait until signal
              LockSupport.parkUntil(100L)
            }
          }
        } catch {
          case ex: Exception => LOG.warning(s"Exception in receiver thread ${ex.getMessage}")
        } finally {
          LOG.info(s"Receiver thread finished")
          if(connection.isDefined) disconnect()
        }
      }
    })
    t.setDaemon(true)
    t.setName("DataReceiver")
    sr.addEventListener(new SerialPortEventListener {
      def serialEvent( ev: SerialPortEvent  ) {
        ev.getEventType match {
          case SerialPortEvent.DATA_AVAILABLE => LOG.fine("Data available"); LockSupport.unpark(t)
          case et => LOG.info(s"Received event $et")
        }
      }
    })
    t.start()
    t
  }
  // Subscriber methods
  private[this] var subscription: Subscription = null

  def onSubscribe(s: Subscription) = {
    require(null == subscription, "Only one subscription supported by serial port")
    subscription = s
  }

  def onNext(t: Byte) = {
    connection.foreach { case(c,_) =>
      try {
        c.getOutputStream.write(t)
      } catch {
        case iot: IOException =>
          LOG.severe(s"IO error on output to serial port ${iot.getMessage}")
          disconnect()
      } finally {
        Future(subscription.request(1))
      }
    }
  }
  def onComplete() = {}
  def onError(t: Throwable) = {}
  // Publisher methods

  class ReceiverSubscription(sub: Subscriber[Byte]) extends Subscription {
    private[this] var requested = 0L
    val lock = new ReentrantLock(true)
    val hasRequested = lock.newCondition()


    def request(n: Long) = {
      lock.lockInterruptibly()
      try {
        requested += n
        hasRequested.signalAll()
      } finally {
        lock.unlock()
      }
    }

    def cancel() = disconnect(true)

    def next(v: Int) {
      lock.lockInterruptibly()
      try {
        while (requested <= 0 && connection.isDefined) hasRequested.await()
        if (connection.isDefined) {
          sub.onNext(v.toByte)
          requested -= 1L
        }
      } finally {
        lock.unlock()
      }
    }
    
    def onDisconnect() {
      lock.lockInterruptibly()
      try {
        sub.onComplete()
        hasRequested.signalAll()
      } finally {
        lock.unlock()
      }
    }
  }

  private[this] var inputSubscribtion: ReceiverSubscription = null

  def subscribe(s: Subscriber[_ >: Byte]) = {
    require(null == inputSubscribtion, "Only one subscriber supported by serial port")
    inputSubscribtion = new ReceiverSubscription(s.asInstanceOf[Subscriber[Byte]])
    s.onSubscribe(inputSubscribtion)
  }
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
}