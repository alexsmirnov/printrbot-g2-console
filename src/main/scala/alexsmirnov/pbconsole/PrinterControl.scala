package alexsmirnov.pbconsole

import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.VBox

class PrinterControl(printer: PrinterModel,settings: Settings) {
  
  val temperature = new TemperatureControl(printer)
  val jogger = new Jogger(printer,settings)
  val node: Node = new BorderPane {
    right = temperature.node
    left = jogger.node
  }
}