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

class Jogger(printer: PrinterModel) {

  def moveButton(label: String, move: String => Unit): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => move(label) }
    disable <== printer.connected.not()
  }
  def macroButton(label: String, commands: String*): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => commands.foreach(printer.sendLine(_, Source.Monitor)) }
    disable <== printer.connected.not()
  }
  def move(axis: String, distance: String) {
    val relative: Boolean = printer.relativePositioning()
    if (!relative) printer.sendLine("G91", Source.Monitor)
    printer.sendLine("G0 " + axis + distance, Source.Monitor)
    if (!relative) printer.sendLine("G90", Source.Monitor)
  }
  def moveX(distance: String): Unit = move("X", distance)
  def moveY(distance: String): Unit = move("Y", distance)
  def moveZ(distance: String): Unit = move("Z", distance)
  def moveE(distance: String): Unit = {
    val relative: Boolean = printer.extruderRelativePositioning()
    if (!relative) printer.sendLine("M83", Source.Monitor)
    printer.sendLine("G0 E" + distance, Source.Monitor)
    if (!relative) printer.sendLine("M82", Source.Monitor)
  }
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
    grid
  }

  val macros = ObservableBuffer("Bed Level" -> Seq("G38.4",
    "M400",
    "G32",
    "M400",
    "G92 Z15.6",
    "M400",
    "G0X50Y100Z40F5000"))
  def controls = macros.map { case (name,commands) => macroButton(name, commands: _*) }
  val node: Node = new BorderPane {
    center = xyJogger
    right = new VBox {
      children = new Label("Z") +: allSteps.reverse.map {
        case (label, index) =>
          moveButton(label, moveZ)
      }
    }
    bottom = new VBox {
      children = List(new HBox {
        children = new Label("Extruder") +: allSteps.map {
          case (label, index) =>
            moveButton(label, moveE)
        }
      },
      new FlowPane {
        children = controls
      })
    }
  }
}