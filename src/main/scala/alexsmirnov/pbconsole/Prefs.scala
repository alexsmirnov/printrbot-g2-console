package alexsmirnov.pbconsole

import scala.collection.JavaConverters._
import scalafx.collections.ObservableBuffer
import scalafx.scene.Node
import scalafx.scene.control.Accordion
import scalafx.scene.control.TextArea
import scalafx.scene.control.TitledPane
import scalafx.scene.layout.HBox
import scalafx.scene.layout.Priority
import scalafx.scene.layout.VBox

class Prefs(settings: Settings) {

  val props = new VBox {

  }

  def macroPane(m: Macro) = {
    new TitledPane {
      text <== m.nameProperty
      content = new TextArea {
        text <== m.contentProperty
      }
    }
  }

  def macroPanes = settings.macros.map(macroPane)

  val macros = new Accordion {
    vgrow = Priority.Always
    hgrow = Priority.Always
    panes = macroPanes
  }

  settings.macros.onChange((_, changes) => {
    for (change <- changes)
      change match {
        case ObservableBuffer.Add(pos, added) =>
          val addedPanes = added.toSeq.map { m => macroPane(m).delegate }
          macros.panes.addAll(pos, addedPanes.asJavaCollection)
        case ObservableBuffer.Remove(pos, removed) => macros.panes.remove(pos, pos + removed.size)
        case ObservableBuffer.Reorder(from, to, permutation) => macros.panes = macroPanes
        case ObservableBuffer.Update(pos, updated) => // already bound to controls
      }
  })
  val node: Node = new HBox {
    children = List()
  }
}