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
      styleClass += "prefs"
      padding = Insets(18)
      gridLinesVisible = true
    }
    rows.zipWithIndex.foreach {
      case ((label, control), r) =>
        val lbl = new Label(label)
        lbl.styleClass += "prefs_label"
        lbl.labelFor = control
        grid.addRow(r, lbl, control)
    }
    grid
  }

  def doubleText(prop: DoubleProperty) = {
    val formatter = new TextFormatter(new NumberStringConverter())
    formatter.value <==> prop
    val text = new TextField {
      styleClass += "prefs_input"
      textFormatter = formatter
    }
    text
  }
  def textField(value: StringProperty) = new TextField {
    styleClass += "prefs_input"
    text <==> value
  }
  def textEdit(value: StringProperty) = new TextArea {
    styleClass += "prefs_input"
    text <==> value
  }

  val props = controlGrid(
    "Bed width" -> doubleText(settings.bedWidth),
    "Bed depth" -> doubleText(settings.bedDepth),
    "Height" -> doubleText(settings.height),
    "Printhead Z offset" -> doubleText(settings.zOffset),
    "Jogger interval" -> doubleText(settings.joggerInterval),
    "Jogger XY step" -> doubleText(settings.jogXYstep),
    "Jogger XY speed" -> doubleText(settings.jogXYspeed),
    "Jogger Z step" -> doubleText(settings.jogZstep),
    "Jogger Z speed" -> doubleText(settings.jogZspeed),
    "Jogger Extruder step" -> doubleText(settings.jogEstep),
    "Jogger Extruder speed" -> doubleText(settings.jogEspeed),
    "Job start GCode" -> textEdit(settings.jobStart),
    "Job end GCode" -> textEdit(settings.jobEnd),
    "Pause start GCode" -> textEdit(settings.pauseStart),
    "Pause end GCode" -> textEdit(settings.pauseEnd),
    "Upload folder" -> textField(settings.uploadFolder))
    
  props.id = "prefs_props"
  def macroPane(m: Macro) = {
    new TitledPane {
      styleClass += "prefs_macro"
      text <== m.nameProperty
      graphic = new Button {
        styleClass += "prefs_remove_macro"
        text = "Remove"
        onAction = { ae: ActionEvent => settings.macros.remove(m) }
      }
      content = controlGrid(
        "Name" -> textField(m.nameProperty),
        "Description" -> textField(m.descriptionProperty),
        "GCode" -> textEdit(m.contentProperty))
      content().styleClass += "macro_props"
    }
  }

  def macroPanes = settings.macros.map(macroPane)

  val macros = new Accordion {
    id = "prefs_macros"
    vgrow = Priority.Always
    hgrow = Priority.Always
  }
  settings.macros.bindMap(macros.panes)(macroPane)
  val node: Node = new HBox {
    id = "prefs"
    children = List(props, new VBox {
      children = List(
        new Button {
          id = "add_macro"
          text = "Add Macro"
          onAction = { ae: ActionEvent => val m = new Macro; m.name = "New Macro"; settings.macros.add(m) }
        }, macros)
    })
  }
}