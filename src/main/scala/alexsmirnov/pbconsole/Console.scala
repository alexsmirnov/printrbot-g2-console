package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.beans.binding.StringBinding
import scalafx.beans.property.BooleanProperty
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Button
import scalafx.scene.control.ListCell
import scalafx.scene.control.ListView
import scalafx.scene.control.SelectionMode
import scalafx.scene.control.TextField
import scalafx.scene.input.KeyCode
import scalafx.scene.input.KeyEvent
import scalafx.scene.layout.HBox
import scalafx.scene.layout.Priority
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color
import scalafx.scene.control.CheckBox
import alexsmirnov.pbconsole.gcode.GCode
import scalafx.beans.property.ObjectProperty
import java.util.function.Predicate
import scalafx.collections.transformation.FilteredBuffer
import javafx.collections.FXCollections

object Console {

  sealed trait Msg {
    def msg: String
    def src: CommandSource
  }
  case class In(msg: String, src: CommandSource) extends Msg {
    override def toString() = "< " + msg
  }
  case class Out(msg: String, src: CommandSource) extends Msg {
    override def toString() = "> " + msg
  }
}
/**
 * @author asmirnov
 *
 */
class Console(printer: PrinterModel, settings: Settings) { console =>

  val buffer = new ObservableBuffer[Console.Msg](FXCollections.synchronizedObservableList(FXCollections.observableArrayList()))
  val disabled = BooleanProperty(false)
  val debug = settings.debugOutput
  val outputPredicate = debug map { dbg: Boolean =>
    predicate[Console.Msg] { m =>
      if (dbg) true else m.src == CommandSource.Console || m.src == CommandSource.Unknown
    }
  }
  val visibleBuffer = new FilteredBuffer(buffer) {
    predicate <== outputPredicate
  }
  var history: List[String] = Nil

  val node: Node = {
    new VBox {
      id = "console"
      vgrow = Priority.Always
      hgrow = Priority.Always
      padding = Insets(2)
      children = List(
        new HBox {
          id = "console_control"
          children = List(
            new CheckBox {
              margin = Insets(5)
              text = "Debud output"
              selected <==> debug
            }, new Button {
              text = "Clear all"
              onAction = { ae: ActionEvent => buffer.clear() }
            })
        },
        new ListView[Console.Msg](visibleBuffer) {
          id = "console_output"
          vgrow = Priority.Always
          hgrow = Priority.Always
          editable = false
          selectionModel().selectionMode = SelectionMode.Single
          visibleBuffer.onChange{ if(visibleBuffer.size >0) scrollTo(visibleBuffer.size-1) }
          cellFactory = { v =>
            new ListCell[Console.Msg] {
              styleClass += "console_cell"
              val input = item map {
                case null => false
                case Console.In(_, _) => true
                case Console.Out(_, _) => false
              }

              val message = item.map[String, StringBinding] {
                case null => null
                case m => m.msg
              }
              wrapText = false
              text <== message
              textFill <== when(input) choose Color.Green otherwise Color.Black
            }

          }
        },
        new HBox {
          id = "console_input"
          hgrow = Priority.Always
          var currentHistory: Int = 0
          filterEvent(KeyEvent.KeyPressed) { ev: KeyEvent =>
            if (ev.code == KeyCode.Up) {
              if (currentHistory < history.size) {
                val s = history(currentHistory)
                input.text = s
                input.positionCaret(s.size)
                input.requestFocus()
                currentHistory += 1
              }
              ev.consume()
            } else if (ev.code == KeyCode.Down) {
              if (currentHistory > 0) {
                currentHistory -= 1
                val s = if (currentHistory > 0) history(currentHistory - 1) else ""
                input.text = s
                input.positionCaret(s.size)
                input.requestFocus()
              }
              ev.consume()
            }
          }
          def send {
            if (!input.text().trim().isEmpty()) {
              if (printer.offer(GCode(input.text()), CommandSource.Console)) {
                history = input.text() :: history
                currentHistory = 0
                input.clear()
                input.requestFocus()
              }
            }
          }
          val input = new TextField {
            id = "console_input_text"
            hgrow = Priority.Always
            onAction = { ae: ActionEvent => send }
            disable <== console.disabled
          }
          children = List(
            input,
            new Button {
            id = "console_send"
              text = "Send"
              onAction = { ae: ActionEvent => send }
              disable <== console.disabled
            })
        })
    }
  }

  def isConsoleSource(src: CommandSource) = debug.value || src == CommandSource.Console || src == CommandSource.Unknown
  // TODO - configure console buffer
  def addInput(src: CommandSource, line: String) {
    if (buffer.size > 10000) buffer.remove(0)
    buffer += Console.In(line, src)
  }

  def addOutput(src: CommandSource, line: String) {
    if (buffer.size > 10000) buffer.remove(0)
    buffer += Console.Out(line, src)
  }

  disabled <== printer.connected.not
  printer.addReceiveListener(addInput)
  printer.addSendListener(addOutput)
}

