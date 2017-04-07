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
  val data = new SimplePublisher[Request]
  
  var commandsStack: List[CommandRequest] = Nil
  
  val linemode = new ProcessorBase[Request,String](4){
    def onNext(r: Request) {
      r match {
        case cr: CommandRequest =>
          this.synchronized(
            commandsStack = cr :: commandsStack
          )
          sendNext(r.line)
        case _ =>
          sendNext(r.line)
          request(1L)
      }
    }
    
  }
  val barrier = new ProcessorBase[Response,Response](){
      
    def onNext(resp: Response) {
      resp match {
        case cr: CommandResponse =>
          val commandRequest = linemode.synchronized(
              commandsStack match {
                case qr :: rest => commandsStack = rest ; Some(qr)
                case Nil => None
              }
              )
          commandRequest match {
            case Some(command) => command.onCommandResponse(cr);linemode.request(1L)
            case None => sendNext(resp)
          }
        case _ =>
         sendNext(resp)
      }
      request(1L)
    }
  }

  val dataLine = data.async(10).transform(linemode)
  
  val commands = new SimplePublisher[String]

  val lines = dataLine.merge(commands.async()).fork
  lines.transform(linesToBytes).subscribe(port)
  
  // input pipeline
  val responses = port.transform(toLines).map(responseParser(_)).transform(barrier)

  def addStateListener(l: Port.StateEvent => Unit) = port.addStateListener(l)

  def sendLine(line: String): Unit = { commands.sendNext(line) }

  def sendData(dataLine: Request): Unit = data.sendNext(dataLine)

  def addReceiveListener(r: Response => Unit): Unit = responses.subscribe(listener(r))

  def addSendListener(l: String => Unit): Unit = lines.subscribe(listener(l))
  
}