package alexsmirnov.pbconsole

import alexsmirnov.pbconsole.serial.Port
import alexsmirnov.pbconsole.serial.PortStub
import scala.collection.{ Map, Seq, mutable }
import scalafx.beans.property.BooleanProperty
import alexsmirnov.stream._
import alexsmirnov.stream.ReactiveOps._
import alexsmirnov.pbconsole.serial._
import scala.collection.immutable.Queue
import org.reactivestreams.Processor
import org.reactivestreams.Publisher

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
    new Printer(port, SmoothieResponse(_))
  }
}
class Printer(port: Port, responseParser: String => Response,queueSize: Int = 4) {

  def start() {
    port.run()
  }

  def stop() {
    port.close()
  }

  // Build output pipeline. each source has it's own asynchonous stream to not block concurrent sources
  val data = Seq[CommandSource](CommandSource.Console,CommandSource.Job,CommandSource.Monitor).
                 map{ s => s-> new SimplePublisher[Request] }.toMap

  @volatile
  var commandsStack: Queue[CommandRequest] = Queue.empty

  val linemode = new Processor[Request,Request] with PublisherBase[Request] with SubscriberBase[Request] {
    def clearStack() { this.synchronized(commandsStack = Queue.empty) }
    def onStart() { clearStack();request(queueSize) }
    def onStop() { cancel();clearStack() }

    def onComplete() = {
      sendComplete()
      clearStack()
    }
    
    def onError(t: Throwable) {
      stopProducer()
      sendError(t)
      clearStack()
    }
    def onNext(r: Request) {
      r match {
        case cr: CommandRequest =>
          this.synchronized(commandsStack = commandsStack.enqueue(cr))
          sendNext(r)
        case _ =>
          sendNext(r)
          request(1L)
      }
    }

  }
  val barrier = new ProcessorBase[Response, (CommandSource, Response)]() {

    def onNext(resp: Response) {
      val source = resp match {
        case cr: CommandResponse =>
          linemode.synchronized(commandsStack.dequeueOption.
            map { case (command, queue) => commandsStack = queue; command }).map { command =>
            command.onCommandResponse(cr); linemode.request(1L)
            command.source
          }
        case r => linemode.synchronized(commandsStack.headOption).map { command =>
          command.onResponse(r)
          command.source
        }
      }
      sendNext(source.getOrElse(CommandSource.Unknown) -> resp)
      request(1L)
    }
  }
  

  val dataLine = merge(data.values.toSeq.map(_.async(10)): _*).transform(linemode)

  val commands = new SimplePublisher[Request]

  val lines = dataLine.merge(commands.async()).fork
  lines.map(_.line).transform(linesToBytes).subscribe(port)

  // input pipeline
  val responses = port.transform(toLines).map(responseParser(_)).transform(barrier).fork

  def addStateListener(l: Port.StateEvent => Unit) = port.addStateListener(l)

  def sendLine(line: String): Unit = { commands.sendNext(PlainTextRequest(line, CommandSource.Console)) }

  def sendData(dataLine: Request): Unit = data(dataLine.source).sendNext(dataLine)

  def addReceiveListener(r: (CommandSource, Response) => Unit): Unit = responses.subscribe(listener({ case (s, resp) => r(s, resp) }))

  def addSendListener(l: Request => Unit): Unit = lines.subscribe(listener(l))

}