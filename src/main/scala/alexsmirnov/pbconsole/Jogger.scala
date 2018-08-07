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
import alexsmirnov.scalafx.ArrowButton

class Jogger(printer: PrinterModel, settings: Settings) {
  
  def move(axis: String, distance: Double, speed: Double = 1000) = printer.offer({ pos =>
    val command = GCode.parse("G0 " + axis + distance + "F" + speed)
    if (pos.absolute) Iterator(GCode.G91, command, GCode.G90) else Iterator.single(command)
  }, CommandSource.Monitor)

  def moveExtruder(tool: String, distance: Double, speed: Double) = printer.offer({ pos =>
    val cmd = GCode.parse(s"G0 ${tool}${distance} F${speed}")
    if (pos.extruderAbsolute) Iterator(GCode.M83, cmd, GCode.M82) else Iterator.single(cmd)
  }, CommandSource.Monitor)

  val ssize = 2

  @volatile
  var joggerScheduler: Option[ScheduledService[Boolean]] = None
  def joggerButtonArmed(dir: JoggerControl.Dir, armed: Boolean) {
    joggerScheduler.foreach(_.cancel)
    if (armed) {
      def task = dir match {
        case JoggerControl.EMinus | JoggerControl.EPlus => Task[Boolean](moveExtruder(dir.axis, settings.jogEstep.toDouble * dir.sign, settings.jogEspeed.toDouble))
        case JoggerControl.ZMinus | JoggerControl.ZPlus => Task[Boolean](move(dir.axis, settings.jogZstep.toDouble * dir.sign, settings.jogZspeed.toDouble))
        case _ => Task[Boolean](move(dir.axis, settings.jogXYstep.toDouble * dir.sign, settings.jogXYspeed.toDouble))
      }
      val ss = ScheduledService[Boolean](task)
      ss.period = Duration(settings.joggerInterval())
      ss.start()
      joggerScheduler = Some(ss)
    }
  }
  val xyJogger = new JoggerControl()

  xyJogger.disable <== printer.connected.not()
  xyJogger.onAxisArmed(joggerButtonArmed _)
  xyJogger.onHomeAction(printer.offer({ _ => GCode("G28").iterator }, CommandSource.Monitor))
  //  val macros = ObservableBuffer("Bed Level" -> Seq("G38.4",
  //    "M400",
  //    "G32",
  //    "M400",
  //    "G92 Z15.6",
  //    "M400",
  //    "G0X50Y100Z40F5000"))
  val macros = new FlowPane {
    id = "macros"
    padding = Insets(10)
    hgap = 10
    vgap = 10
    alignment = Pos.TopLeft
  }

  settings.macros.bindMap(macros.children) { m =>
    new Button() {
      margin = Insets(5)
      styleClass += "macro"
      text <== m.nameProperty
      onAction = { ae: ActionEvent => printer.offer({ _ => Macro.prepare(m.content, settings).flatMap(GCode(_)) }, CommandSource.Monitor) }
      disable <== printer.connected.not()
    }.delegate
  }
  val extruderArrowBtn ="extruder" ::JoggerControl.ArrowBtn
  val node: Node = new BorderPane {
    id = "jogger"
    padding = Insets(10)
    center = xyJogger
    right = new VBox {
      spacing = 5
      padding = Insets(5)
      alignment = Pos.Center
      children = List(
        new ArrowButton("-E", JoggerControl.arrowDim, ArrowButton.Up) {
          disable <== printer.connected.not()
          styleClass ++= extruderArrowBtn
          armed.onChange(joggerButtonArmed(JoggerControl.EMinus, armed()))
        },
        new ArrowButton("+E", JoggerControl.arrowDim, ArrowButton.Down) {
          disable <== printer.connected.not()
          styleClass ++= extruderArrowBtn
          armed.onChange(joggerButtonArmed(JoggerControl.EPlus, armed()))
        })
    }
    bottom = macros
  }
}