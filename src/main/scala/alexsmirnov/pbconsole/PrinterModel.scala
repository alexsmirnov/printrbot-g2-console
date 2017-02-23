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
  val status = when(connected) choose(port.map[String,StringBinding] { n => s"Connected to $n at " } + speed) otherwise("Disconnected")
  printer.addStateListener(connected.asListener { 
    case Port.Connected(name,baud) => speed() = baud;true
    case Port.Disconnected => false
  })
}