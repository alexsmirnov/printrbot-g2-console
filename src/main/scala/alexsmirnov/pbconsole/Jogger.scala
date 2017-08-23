package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.control.Button
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.GridPane
import scalafx.scene.layout.VBox
import scalafx.scene.layout.HBox
import scalafx.scene.control.Label
import scalafx.event.ActionEvent
import scalafx.collections.ObservableMap
import scalafx.collections.ObservableBuffer
import scalafx.scene.layout.FlowPane
import scalafx.geometry.Insets
import javafx.scene.layout.Priority
import scalafx.geometry.Pos
import alexsmirnov.pbconsole.serial.Printer
import alexsmirnov.pbconsole.gcode.GCode

class Jogger(printer: PrinterModel, settings: Settings) {

  def moveButton(label: String, move: String => Unit): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => move(label) }
    disable <== printer.connected.not()
  }
  def macroButton(label: String, commands: String*): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => printer.offer({ _ => commands.map(GCode(_)).iterator}, CommandSource.Monitor) }
    disable <== printer.connected.not()
  }
  def move(axis: String, distance: String) = printer.offer({ pos =>
    val command = GCode("G0 " + axis + distance)
    if (pos.absolute) Iterator(GCode("G91"),command,GCode("G90")) else Iterator.single(command)
  },CommandSource.Monitor)
  
  def moveX(distance: String): Unit = move("X", distance)
  def moveY(distance: String): Unit = move("Y", distance)
  def moveZ(distance: String): Unit = move("Z", distance)
  def moveE(distance: String): Unit = printer.offer({ pos =>
    val cmd = GCode("G0 E" + distance)
    if (pos.absolute) Iterator(GCode("M83"),cmd,GCode("M83")) else Iterator(cmd)
  },CommandSource.Monitor)
  
  val steps = List("0.1", "1", "10")
  val allSteps = steps.reverse.map("-" + _).zipWithIndex ++: (steps.zipWithIndex.map { case (t, n) => t -> (n + steps.size + 1) })
  val xyJogger = {
    val grid = new GridPane()
    // X
    allSteps.foreach {
      case (label, index) =>
        grid.add(moveButton(label, moveX), index + 1, steps.size + 1)
    }
    // Y
    allSteps.foreach {
      case (label, index) =>
        grid.add(moveButton(label, moveY), steps.size + 1, steps.size * 2 - index + 1)
    }
    grid.add(new Label("X"), 0, steps.size + 1)
    grid.add(new Label("Y"), steps.size + 1, 0)
    grid.add(macroButton("H", "G28"), steps.size + 1, steps.size + 1)
    grid.add(macroButton("Home X", "G28 X0"), 1, 1, 2, 1)
    grid.add(macroButton("Home Y", "G28 Y0"), 1, steps.size * 2 + 1, 2, 1)
    grid.add(macroButton("Home XY", "G28 X0 Y0"), steps.size * 2, 1, 2, 1)
    grid.add(macroButton("Motors Off", "M84"), steps.size * 2, steps.size * 2 + 1, 2, 1)
    grid.alignment = Pos.Center
    // visual effects
    grid.hgap = 5.0
    grid.vgap = 5.0
    grid.padding = Insets(5)
    grid
  }

  //  val macros = ObservableBuffer("Bed Level" -> Seq("G38.4",
  //    "M400",
  //    "G32",
  //    "M400",
  //    "G92 Z15.6",
  //    "M400",
  //    "G0X50Y100Z40F5000"))
  val macros = new FlowPane {
    padding = Insets(10)
  }

  settings.macros.bindMap(macros.children) { m =>
    new Button() {
      margin = Insets(5)
      text <== m.nameProperty
      onAction = { ae: ActionEvent => printer.offer({ _ => Macro.prepare(m.content, settings).map(GCode(_))}, CommandSource.Monitor) }
      disable <== printer.connected.not()
    }.delegate
  }
  val node: Node = new BorderPane {
    padding = Insets(10)
    center = xyJogger
    right = new VBox {
      spacing = 5
      padding = Insets(5)
      alignment = Pos.Center
      children = new Label("Z") +: allSteps.reverse.map {
        case (label, index) =>
          moveButton(label, moveZ)
      }
    }
    bottom = new VBox {
      children = List(new HBox {
        vgrow = Priority.NEVER
        alignment = Pos.Center
        spacing = 5
        padding = Insets(5)
        children = new Label("Extruder") +: allSteps.map {
          case (label, index) =>
            moveButton(label, moveE)
        }
      },
        macros)
    }
  }
}