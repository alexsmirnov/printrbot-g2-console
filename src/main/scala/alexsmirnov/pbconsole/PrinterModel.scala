package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import alexsmirnov.pbconsole.serial.Port
import scalafx.beans.binding.StringBinding

class PrinterModel(val printer: Printer) {
  val connected = BooleanProperty(false)
  val speed = IntegerProperty(0)
  val port = StringProperty("")
  val status = when(connected) choose(port.zip(speed).map[String,StringBinding] { n => s"Connected to ${n._1} at ${n._2}" }) otherwise("Disconnected")
  printer.addStateListener(connected.asListener { 
    case Port.Connected(name,baud) => speed() = baud;port() = name; true
    case Port.Disconnected => false
  })

  def sendLine(line: String): Unit = {
    printer.sendLine(line)
  }

  def addReceiveListener(listener: String => Unit) = {
    printer.addReceiveListener {l =>runInFxThread(listener(l))}
  }
  def addSendListener(listener: String => Unit) = {
    printer.addSendListener {l =>runInFxThread(listener(l))}
  }
}