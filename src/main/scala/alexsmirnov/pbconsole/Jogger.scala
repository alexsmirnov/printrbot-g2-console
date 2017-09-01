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
import scalafx.concurrent.ScheduledService
import javafx.beans.property.DoubleProperty
import scalafx.beans.binding.NumberExpression
import scalafx.concurrent.Task
import scalafx.util.Duration

class Jogger(printer: PrinterModel, settings: Settings) {

  def moveButton(label: String, move: => Unit): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => move }
    disable <== printer.connected.not()
  }
  def macroButton(label: String, commands: String*): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    onAction = { ae: ActionEvent => printer.offer({ _ => commands.map(GCode(_)).iterator}, CommandSource.Monitor) }
    disable <== printer.connected.not()
  }
  def move(axis: String, distance: Double, speed: Double = 1000) = printer.offer({ pos =>
    val command = GCode("G0 " + axis + distance+"F"+speed)
    if (pos.absolute) Iterator(GCode("G91"),command,GCode("G90")) else Iterator.single(command)
  },CommandSource.Monitor)
  
  def moveX(distance: Double): Unit = move("X", distance)
  def moveY(distance: Double): Unit = move("Y", distance)
  def moveZ(distance: Double): Unit = move("Z", distance)
  def moveE(distance: Double): Unit = printer.offer({ pos =>
    val cmd = GCode("G0 E" + distance)
    if (pos.absolute) Iterator(GCode("M83"),cmd,GCode("M83")) else Iterator(cmd)
  },CommandSource.Monitor)
  
  val steps = List[Double](0.1, 1, 10)
  val ssize = steps.size
  val allSteps = steps.reverse.map(- _).zipWithIndex ++: (steps.zipWithIndex.map { case (t, n) => t -> (n + steps.size + 1) })
  var joggerScheduler: Option[ScheduledService[Unit]] = None
  def joggerButton(label: String, axis: String, step: NumberExpression,speed: NumberExpression): Node = new Button(label) {
    minWidth = 45
    minHeight = 45
    disable <== printer.connected.not()
    armed.onChange { 
      joggerScheduler.foreach(_.cancel)
      if(armed()) {
        val ss = ScheduledService[Unit](Task[Unit](move(axis,step.toDouble,speed.toDouble)))
        ss.period = Duration(settings.joggerInterval())
        ss.start()
        joggerScheduler = Some(ss)
      }
    }
  }
  val xyJogger = {
    val grid = new GridPane()
//    // X
//    allSteps.foreach {
//      case (step, index) =>
//        grid.add(moveButton(step.toString(), moveX(step)), index + 1, steps.size + 1)
//    }
//    // Y
//    allSteps.foreach {
//      case (step, index) =>
//        grid.add(moveButton(step.toString(), moveY(step)), steps.size + 1, steps.size * 2 - index + 1)
//    }
    grid.add(joggerButton("-X", "X", -settings.jogXYstep, settings.jogXYspeed),ssize,ssize+1)
    grid.add(joggerButton("+X", "X", settings.jogXYstep, settings.jogXYspeed),ssize+2,ssize+1)
    grid.add(joggerButton("+Y", "Y", settings.jogXYstep, settings.jogXYspeed),ssize+1,ssize)
    grid.add(joggerButton("-Y", "Y", -settings.jogXYstep, settings.jogXYspeed),ssize+1,ssize+2)
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
      children = new Label("Z") :: 
                   joggerButton("Z", "Z", settings.jogZstep, settings.jogZspeed) :: 
                   joggerButton("-Z", "Z", -settings.jogZstep, settings.jogZspeed) :: 
                   Nil
    }
    bottom = new VBox {
      children = List(new HBox {
        vgrow = Priority.NEVER
        alignment = Pos.Center
        spacing = 5
        padding = Insets(5)
      children = new Label("E") :: 
                   joggerButton("E", "E", settings.jogEstep, settings.jogEspeed) :: 
                   joggerButton("-E", "E", -settings.jogEstep, settings.jogEspeed) :: 
                   Nil
      },
        macros)
    }
  }
}