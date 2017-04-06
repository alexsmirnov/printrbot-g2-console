package alexsmirnov.pbconsole

import alexsmirnov.pbconsole.serial.Port
import alexsmirnov.pbconsole.serial.PortStub
import scala.collection.{ Map, Seq, mutable }
import scalafx.beans.property.BooleanProperty
import alexsmirnov.stream._
import alexsmirnov.stream.ReactiveOps._
import alexsmirnov.pbconsole.serial._
import org.json4s._
import org.json4s.native.JsonMethods._

/**
 * @author asmirnov
 *
 */
object Printer {
  def apply(args: Map[String, String]) = {
    val port = args.get("port") match {
      case Some("stub") => new PortStub()
      case Some(portname) => Port(portname.r)
      case None => Port("/dev/tty\\.usbmodem.*".r)
    }
    new Printer(port,SmoothieResponse(_))
  }
}
class Printer(port: Port,responseParser: String => Response) {

  def start() {
    port.run()
  }

  def stop() {
    port.close()
  }

  // Build output pipeline
  val data = new SimplePublisher[String]
  val linemode = new Barrier[String](4)

  val dataLine = transform(transform(data, async[String](10)), linemode)
  val commands = new SimplePublisher[String] 

  val lines = transform(merge(dataLine, transform(commands, async[String]())), new Fork[String])
  transform(lines, linesToBytes).subscribe(port)
  // input pipeline
  val linesIn = transform(port, toLines)
  
  val responses = transform(transform(linesIn,map[String,Response](responseParser(_))), new Fork[Response])

  val commandResponses = collect[Response, Long] { 
    case cr:CommandResponse => 1L
  }
  
  responses.subscribe(commandResponses)
  
  commandResponses.subscribe(linemode.barrier)

  def addStateListener(l: Port.StateEvent => Unit) = port.addStateListener(l)

  def sendLine(line: String): Unit = { linemode.request(-1L); commands.sendNext(line) }

  def sendData(dataLine: String): Unit = data.sendNext(dataLine)

  def addReceiveListener(r: Response => Unit): Unit = responses.subscribe(listener(r))

  def addSendListener(l: String => Unit): Unit = lines.subscribe(listener(l))
  
}