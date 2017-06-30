package alexsmirnov.pbconsole

import scala.collection.JavaConverters._

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Accordion
import scalafx.scene.control.Button
import scalafx.scene.control.Label
import scalafx.scene.control.TextArea
import scalafx.scene.control.TextField
import scalafx.scene.control.TitledPane
import scalafx.scene.layout.GridPane
import scalafx.scene.layout.HBox
import scalafx.scene.layout.Priority
import scalafx.scene.layout.VBox
import scalafx.beans.property.DoubleProperty
import scalafx.scene.control.TextFormatter
import scalafx.util.converter.NumberStringConverter
import javafx.beans.property.StringProperty

class Prefs(settings: Settings) {

  def controlGrid(rows: (String, Node)*): Node = {
    val grid = new GridPane {
      padding = Insets(18)
      gridLinesVisible = true
    }
    rows.zipWithIndex.foreach {
      case ((label, control), r) =>
        grid.addRow(r, new Label(label), control)
    }
    grid
  }
  
  def doubleText(prop: DoubleProperty) = {
    val formatter = new TextFormatter(new NumberStringConverter())
    formatter.value <==> prop
    val text = new TextField {
          textFormatter = formatter
        }
    text
  }
  def textField(value: StringProperty) = new TextField {
          text <==> value
        }
  def textEdit(value: StringProperty) = new TextArea {
          text <==> value
        }

  val props = controlGrid(
      "Bed width" -> doubleText(settings.bedWidth),
      "Bed depth" -> doubleText(settings.bedDepth),
      "Height" -> doubleText(settings.height),
      "Printhead Z offset" -> doubleText(settings.zOffset),
      "Job start GCode" -> textEdit(settings.jobStart),
      "Job End GCode" -> textEdit(settings.jobEnd),
      "Upload folder" -> textField(settings.uploadFolder)
      )
  
      def macroPane(m: Macro) = {
    new TitledPane {
      text <== m.nameProperty
      graphic = new Button {
        text = "Remove"
        onAction ={ ae: ActionEvent => settings.macros.remove(m) } 
      }
      content = controlGrid(
        "Name" -> textField(m.nameProperty),
        "Description" -> textField(m.descriptionProperty),
        "GCode" -> textEdit(m.contentProperty)
        )
    }
  }

  def macroPanes = settings.macros.map(macroPane)

  val macros = new Accordion {
    vgrow = Priority.Always
    hgrow = Priority.Always
//    panes = macroPanes
  }
  settings.macros.bindMap(macros.panes)(macroPane)
  /*
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
  */
  val node: Node = new HBox {
    children = List(props, new VBox {
      children = List(
        new Button {
          text = "Add Macro"
          onAction ={ ae: ActionEvent => val m = new Macro;m.name = "New Macro";settings.macros.add(m) }
        }, macros)
    })
  }
}