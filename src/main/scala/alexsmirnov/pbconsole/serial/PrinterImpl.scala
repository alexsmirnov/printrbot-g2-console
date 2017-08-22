package alexsmirnov.pbconsole.serial

import scala.collection.{ Map, Seq, mutable }
import scalafx.beans.property.BooleanProperty
import alexsmirnov.stream._
import alexsmirnov.stream.ReactiveOps._
import alexsmirnov.pbconsole.serial._
import scala.collection.immutable.Queue
import org.reactivestreams.Processor
import org.reactivestreams.Publisher
import alexsmirnov.pbconsole.gcode.CommandResponse
import alexsmirnov.pbconsole.CommandSource
import alexsmirnov.pbconsole.gcode.Response
import alexsmirnov.pbconsole.gcode.SmoothieResponse
import alexsmirnov.pbconsole.gcode.PlainTextRequest
import alexsmirnov.pbconsole.gcode.GCode
import scala.concurrent.Future
import scala.concurrent.Promise

/**
 * @author asmirnov
 *
 */
object PrinterImpl {
  def apply(args: Map[String, String]) = {
    val port = args.get("port") match {
      case Some("stub") => new PortStub()
      case Some(portname) => Port(portname.r)
      case None => Port("/dev/tty\\.usbmodem.*".r)
    }
    new PrinterImpl(port, SmoothieResponse(_))
  }

  sealed trait Request {
    def gcode: GCode
    def src: CommandSource
    def onResponse(response: Response): Unit
    def onComplete(response: Response): Unit
  }
  type RequestProducer = Printer.Positioning => Iterator[Request]

  class SimpleRequest(val gcode: GCode, val src: CommandSource) extends Request {
    def onResponse(response: Response) = ()
    def onComplete(response: Response) = ()
  }
  class QueryRequest(val gcode: GCode, val src: CommandSource, promise: Promise[List[Response]]) extends Request {
    private[this] var responses: List[Response] = Nil
    def onResponse(response: Response) = responses = response :: responses
    def onComplete(response: Response) = promise.success((response :: responses).reverse)
  }

  def singleCommand(gc: GCode, source: CommandSource): RequestProducer = { pp =>
    Iterator.single(new SimpleRequest(gc, source))
  }

}

class PrinterImpl(port: Port, responseParser: String => Response, queueSize: Int = 4) extends Printer {

  import PrinterImpl._

  def start() {
    port.run()
  }

  def stop() {
    port.close()
  }

  def reconnect() = port.disconnect(true)

  // Build output pipeline. each source has it's own asynchonous stream to not block concurrent sources
  val data = new SimplePublisher[(GCode, CommandSource)]
  val commands = new SimplePublisher[RequestProducer]

  @volatile
  var commandsStack: Queue[Request] = Queue.empty

  val linemode = new Processor[Request, Request] with PublisherBase[Request] with SubscriberBase[Request] {
    def clearStack() { this.synchronized(commandsStack = Queue.empty) }
    def onStart() {
      positioning = Printer.Positioning(true, true)
      clearStack(); request(queueSize)
    }
    def onStop() { cancel(); clearStack() }

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
      r.gcode match {
        case cr: GCode.Command =>
          this.synchronized(commandsStack = commandsStack.enqueue(r))
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
            command.onComplete(cr); linemode.request(1L)
            command.src
          }
        case r => linemode.synchronized(commandsStack.headOption).map { command =>
          command.onResponse(r)
          command.src
        }
      }
      sendNext(source.getOrElse(CommandSource.Unknown) -> resp)
      request(1L)
    }
  }

  @volatile
  var positioning: Printer.Positioning = Printer.Positioning(true, true)
  private def setPositioning(gcode: GCode) = gcode match {
    case GCode.GCommand(90, _, _) => positioning = Printer.Positioning(true, true)
    case GCode.GCommand(91, _, _) => positioning = Printer.Positioning(false, false)
    case GCode.MCommand(82, _, _) => positioning = positioning.copy(extruderAbsolute = true)
    case GCode.MCommand(83, _, _) => positioning = positioning.copy(extruderAbsolute = false)
    case _ => ()
  }
  val dataLine = data.map { case (gc, src) => singleCommand(gc, src) }.
    merge(commands.async(5)).
    flatMap { rp => rp(positioning).map { req => setPositioning(req.gcode); req }.toTraversable }.
    transform(linemode)

  val lines = dataLine.fork
  lines.map(_.gcode.line).transform(linesToBytes).subscribe(port)

  // input pipeline
  val responses = port.transform(toLines).map(responseParser(_)).transform(barrier).fork

  def addStateListener(l: Port.StateEvent => Unit) = port.addStateListener(l)

  def offerCommands(cmds: Printer.Positioning => Iterator[GCode], src: CommandSource): Boolean =
    commands.offer({ rp => cmds(rp).map { gcode => new PrinterImpl.SimpleRequest(gcode, src) } })

  def query(command: GCode, src: CommandSource): Future[List[Response]] = {
    val promise = Promise[List[Response]]
    if (commands.offer({ rp => Iterator.single(new PrinterImpl.QueryRequest(command, src, promise)) })) {
      promise.future
    } else {
      Future.failed(new Exception("command queue full"))
    }
  }

  def sendData(command: GCode, src: CommandSource): Boolean = data.sendNext(command -> src)

  def addReceiveListener(r: (Response, CommandSource) => Unit): Unit = responses.subscribe(listener({ case (resp, s) => r(s, resp) }))

  def addSendListener(l: (GCode, CommandSource) => Unit): Unit = ??? // lines.subscribe(listener(l))

}