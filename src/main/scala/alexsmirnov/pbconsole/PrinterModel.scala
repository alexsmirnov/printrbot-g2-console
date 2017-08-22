package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import alexsmirnov.pbconsole.serial.Port
import scalafx.beans.binding.StringBinding
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scalafx.beans.property.FloatProperty
import scalafx.beans.property.DoubleProperty
import alexsmirnov.pbconsole.serial.PrinterImpl
import alexsmirnov.pbconsole.gcode.StatusResponse
import alexsmirnov.pbconsole.gcode.ResponseValue
import alexsmirnov.pbconsole.gcode.Request
import alexsmirnov.pbconsole.gcode.QueryCommand
import alexsmirnov.pbconsole.gcode.GCode

object PrinterModel {
  class Heater {
    val temperature = FloatProperty(0.0f)
    val target = FloatProperty(0.0f)
    val output = IntegerProperty(0)
  }
  class Position {
    val x = DoubleProperty(0.0)
    val y = DoubleProperty(0.0)
    val z = DoubleProperty(0.0)
    val extruder = DoubleProperty(0.0)
  }
}
class PrinterModel(printer: PrinterImpl) {
  import PrinterModel._
  // Current status
  val connected = BooleanProperty(false)
  val speed = IntegerProperty(0)
  val port = StringProperty("")

  val extruder = new Heater
  val bed = new Heater
  val position = new Position

  val status = when(connected) choose (port.zip(speed).map[String, StringBinding] { n => s"Connected to ${n._1} at ${n._2}" }) otherwise ("Disconnected")

  printer.addStateListener(connected.asListener {
    case Port.Connected(name, baud) =>
      speed() = baud; port() = name; true
    case Port.Disconnected => false
  })

  def offerCommand(gcode: GCode, src: CommandSource): Boolean = {
    printer.offerCommands({ _ => Iterator.single(gcode) }, src)
  }

  def offerQuery(gcode: GCode, src: CommandSource): Future[List[ResponseValue]] = {
    printer.query(gcode, src).map { rsps =>
      rsps.flatMap {
        case sr: StatusResponse => sr.values
        case _ => Nil
      }
    }
  }

  def addReceiveListener(listener: (CommandSource, String) => Unit) = {
    printer.addReceiveListener { (r, s) => runInFxThread(listener(s, r.rawLine)) }
  }

  def addSendListener(listener: (CommandSource, String) => Unit) = {
    printer.addSendListener { (gcode,s) => runInFxThread(listener(s,gcode.line))}
  }
}