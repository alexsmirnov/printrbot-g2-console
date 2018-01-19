package alexsmirnov.pbconsole.serial

import java.io.IOException
import java.util.concurrent.locks.LockSupport

import scala.collection.{ mutable => m }
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.matching.Regex

import alexsmirnov.stream.PublisherBase
import alexsmirnov.stream.SubscriberBase
import gnu.io.NRSerialPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener

/**
 * @author asmirnov
 *
 */
class NRJavaSerialPort(port: Regex, baud: Int = 115200) extends Port with PublisherBase[Byte] with SubscriberBase[Byte] {
  import Port._
  val listeners = m.Buffer[(Port.StateEvent => Unit)]()
  val mutex = new Object
  def addStateListener(listener: Port.StateEvent => Unit) = listeners += listener

  @volatile
  private[this] var connection: Option[(NRSerialPort, Thread)] = None

  def run() {
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
      case Failure(err) => LOG.error(s"Error in port watch process ${err.getMessage}")
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
        request(if (buffSize > 0) buffSize else 1024)
        val inThread = startReceiver(sr)
        connection = Some((sr, inThread))
      } else {
        LOG.error("failed to connect serial port")
        waitForConnect
      }
    }
  }

  def disconnect(reconnect: Boolean = true) {
    LOG.info(s"Disconnect  $connection")
    mutex.synchronized {
      connection.foreach {
        case (p, t) =>
          connection = None
          // stop read thread before disconnect
          if (Thread.currentThread() != t) t.interrupt()
          // stop all streams
          sendComplete()
          cancel()
          listeners.foreach { _(Disconnected) }
          p.disconnect()
          if (reconnect) waitForConnect
      }
    }
  }

  def startReceiver(sr: NRSerialPort) = {
    val t = new Thread(new Runnable {
      def run() {
        try {
          while (!Thread.interrupted() && sr.isConnected()) {
            val lastByte = sr.getInputStream.read()
            if (lastByte >= 0) {
              sendNext(lastByte.toByte)
            } else {
              // EOF - no data to read. wait until signal
              LockSupport.parkUntil(100L)
            }
          }
        } catch {
          case ex: Exception => LOG.warn(s"Exception in receiver thread ${ex.getMessage}")
        } finally {
          LOG.info(s"Receiver thread finished")
          if (connection.isDefined) disconnect()
        }
      }
    })
    t.setDaemon(true)
    t.setName("DataReceiver")
    sr.addEventListener(new SerialPortEventListener {
      def serialEvent(ev: SerialPortEvent) {
        ev.getEventType match {
          case SerialPortEvent.DATA_AVAILABLE =>
            LOG.trace("Data available"); LockSupport.unpark(t)
          case et => LOG.info(s"Received event $et")
        }
      }
    })
    t.start()
    t
  }

  def onNext(t: Byte) = {
    connection.foreach {
      case (c, _) =>
        try {
          c.getOutputStream.write(t)
        } catch {
          case iot: IOException =>
            LOG.error(s"IO error on output to serial port ${iot.getMessage}")
            disconnect()
        } finally {
          request(1)
        }
    }
  }
  def onComplete() = {}
  def onError(t: Throwable) = {}
  // Publisher methods

  protected def onStart(): Unit = {}

  protected def onStop(): Unit = {}
}
