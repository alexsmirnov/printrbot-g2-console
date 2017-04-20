package alexsmirnov.pbconsole

import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.VBox

class PrinterControl(printer: PrinterModel) {
  
  val temperature = new TemperatureControl(printer)
  val jogger = new Jogger(printer)
  val node: Node = new BorderPane {
    right = temperature.node
    left = jogger.node
  }
}