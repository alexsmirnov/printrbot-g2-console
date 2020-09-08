package alexsmirnov.pbconsole

import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.VBox
import alexsmirnov.pbconsole.print.JobModel
import alexsmirnov.pbconsole.print.JobControl
import scalafx.scene.layout.HBox

class PrinterControl(printer: PrinterModel,  settings: Settings) {
  
  val temperature = new TemperatureControl(printer)
  val jogger = new Jogger(printer,settings)
  val node: Node = new HBox {
    id = "printer_control"
    children = List(jogger.node, temperature.node)
  }
}