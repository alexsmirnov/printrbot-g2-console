package alexsmirnov.pbconsole

import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.VBox

class PrinterControl {
  
  val temperature = new TemperatureControl
  val node: Node = new BorderPane {
    right = temperature.node
  }
}