package alexsmirnov.pbconsole.serial

import alexsmirnov.pbconsole.gcode.Request
import alexsmirnov.pbconsole.gcode.Response
import alexsmirnov.pbconsole.CommandSource
import alexsmirnov.pbconsole.gcode.GCode
import scala.concurrent.Future

object Printer {
  case class Positioning(absolute: Boolean = true, extruderAbsolute: Boolean = true)
  type GCodeProducer = Positioning => Iterator[GCode]
}

trait Printer {

  /**
   * Try to add collection of commands to buffer. All commands will be sent to printer
   * in sequence, without interleaving with other sources.
   * @param commands function that creates collection of GCode lines. 
   * Used current settings as parameter to generate correct code, used to generate correct joging commands
   * @param src source service
   * @return true if commands submitted to queue, false if it full
   */
  def offerCommands(commands: Printer.GCodeProducer, src: CommandSource): Boolean
  
  /**
   * Submit query to printer ( e.g ask for temperature, position or settings )
   * @param command GCode to send
   * @param src source service
   * @return Future with collected responses. in input queue is full or printer is disconnected,
   *  return failed future.
   */
  def query(command: GCode, src: CommandSource): Future[List[Response]]

  /**
   * Send all commands from iterator to printer, used to print job. Lines can interleave with
   * other submitted by offerCommand and query.
   * @param command print job command
   * @param src source service
   */
  def sendData(command: GCode, src: CommandSource): Boolean

  /**
   * add listener for all responses received by printer
   * @param r
   */
  def addReceiveListener(r: (Response,CommandSource) => Unit): Unit

  /**
   * add listener that will be called with for all lines sent to printer. Used to monitor printer communications
   * @param l
   */
  def addSendListener(l: (GCode,CommandSource) => Unit): Unit

  def start(): Unit

  def stop(): Unit

  def reconnect(): Unit

  def addStateListener(l: Port.StateEvent => Unit): Unit

}