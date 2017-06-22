package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import alexsmirnov.pbconsole.serial.Port
import scalafx.beans.binding.StringBinding
import scala.concurrent.Future
import scala.concurrent.Promise
import scalafx.beans.property.FloatProperty
import scalafx.beans.property.DoubleProperty

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
class PrinterModel(printer: Printer) {
  import PrinterModel._
  // Current status
  val connected = BooleanProperty(false)
  val speed = IntegerProperty(0)
  val port = StringProperty("")
  
  val extruder = new Heater
  val bed = new Heater
  val position = new Position
  val relativePositioning = BooleanProperty(false)
  val extruderRelativePositioning = BooleanProperty(false)
  
  val status = when(connected) choose(port.zip(speed).map[String,StringBinding] { n => s"Connected to ${n._1} at ${n._2}" }) otherwise("Disconnected")
  
  printer.addStateListener(connected.asListener { 
    case Port.Connected(name,baud) => speed() = baud;port() = name; relativePositioning() = false;extruderRelativePositioning() = false;true
    case Port.Disconnected => false
  })

  private def setPositioning(line: String) = line.trim match {
    case Request.GCmd("90") => relativePositioning() = false; extruderRelativePositioning() = false
    case Request.GCmd("91") => relativePositioning() = true; extruderRelativePositioning() = true
    case Request.MCmd("82") => extruderRelativePositioning() = false
    case Request.MCmd("83") => extruderRelativePositioning() = true
    case _ => ()
  }
  
  def sendLine(line: String,src: CommandSource): Unit = {
    setPositioning(line)
    printer.sendData(Request(line,src))
  }
  
  def sendQuery(query: String,src: CommandSource): Future[List[ResponseValue]] = {
    setPositioning(query)
    val promise = Promise[List[ResponseValue]]
    printer.sendData(QueryCommand(query,src,{
      case sr: StatusResponse => promise.success(sr.values)
      case other => promise.failure(new Throwable(s"unexpected response $other"))
    }))
    promise.future
  }

  def addReceiveListener(listener: (CommandSource,String) => Unit) = {
    printer.addReceiveListener {(s,l) => runInFxThread(listener(s,l.rawLine))}
  }
  
  def addSendListener(listener: (CommandSource,String) => Unit) = {
    printer.addSendListener {l => runInFxThread(listener(l.source,l.line))}
  }
}