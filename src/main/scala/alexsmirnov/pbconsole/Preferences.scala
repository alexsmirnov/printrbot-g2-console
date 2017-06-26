package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.layout.HBox
import scalafx.scene.layout.VBox
import scalafx.scene.control.Accordion
import scalafx.scene.layout.Priority
import scalafx.scene.control.TitledPane
import scalafx.scene.control.TextArea

class Preferences(settings: Settings) {
  
  val props = new VBox {
    
  }
  val macroPanes = settings.macros.map { case Macro(name,description,cont) =>

        new TitledPane {
          text = name
          content = new TextArea {
            text = cont
          }
        }
      }
  val macros = new Accordion {
      vgrow = Priority.Always
      hgrow = Priority.Always
      panes = macroPanes
  }
  
  val node: Node = new HBox {
    children = List()
  }
}