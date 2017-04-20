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

class Jogger(printer: PrinterModel) {

  def moveButton(label: String, move: String => Unit): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => move(label) }
  }
  def move(axis: String,distance: String) {
    val relative: Boolean = printer.relativePositioning()
    if(!relative) printer.sendLine("G91", Source.Monitor)
    printer.sendLine("G0 "+axis+distance, Source.Monitor)
    if(!relative) printer.sendLine("G90", Source.Monitor)
  }
  def moveX(distance: String): Unit = move("X",distance)
  def moveY(distance: String): Unit = move("Y",distance)
  def moveZ(distance: String): Unit = move("Z",distance)
  def moveE(distance: String): Unit =  {
    val relative: Boolean = printer.extruderRelativePositioning()
    if(!relative) printer.sendLine("M83", Source.Monitor)
    printer.sendLine("G0 E"+distance, Source.Monitor)
    if(!relative) printer.sendLine("M82", Source.Monitor)
  }
  val steps = List("0.1", "1", "10")
  val allSteps = steps.reverse.map("-" + _).zipWithIndex ++: (steps.zipWithIndex.map { case (t, n) => t -> (n + steps.size + 1) })
  val xyJogger = {
    val grid = new GridPane()
    // X
    allSteps.foreach {
      case (label, index) =>
        grid.add(moveButton(label, moveX), index+1, steps.size+1)
    }
    // Y
    allSteps.foreach {
      case (label, index) =>
        grid.add(moveButton(label, moveY), steps.size+1, index+1)
    }
    grid.add(new Label("X"),0,steps.size+1)
    grid.add(new Label("Y"),steps.size+1,0)
    grid
  }

  val node: Node = new BorderPane {
    center = xyJogger
    right = new VBox {
      children = new Label("Z") +: allSteps.reverse.map {
        case (label, index) =>
          moveButton(label, moveZ)
      }
    }
    bottom = new HBox {
      children = new Label("Extruder") +: allSteps.map {
        case (label, index) =>
          moveButton(label, moveE)
      }
    }
  }
}