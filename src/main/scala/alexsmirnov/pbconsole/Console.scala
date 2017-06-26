package alexsmirnov.pbconsole

import scalafx.Includes.eventClosureWrapperWithParam
import scalafx.Includes.jfxActionEvent2sfx
import scalafx.Includes.jfxKeyEvent2sfx
import scalafx.Includes.jfxMultipleSelectionModel2sfx
import scalafx.Includes.when
import scalafx.beans.binding.BooleanBinding.sfxBooleanBinding2jfx
import scalafx.beans.binding.StringBinding
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.BooleanProperty.sfxBooleanProperty2jfx
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Button
import scalafx.scene.control.ListCell
import scalafx.scene.control.ListView
import scalafx.scene.control.SelectionMode
import scalafx.scene.control.TextField
import scalafx.scene.control.TextField.sfxTextField2jfx
import scalafx.scene.input.KeyCode
import scalafx.scene.input.KeyEvent
import scalafx.scene.layout.HBox
import scalafx.scene.layout.Priority
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color
import scalafx.scene.paint.Color.sfxColor2jfx
import scalafx.scene.control.CheckBox

object Console {

  sealed trait Msg {
    def msg: String
  }
  case class In(msg: String) extends Msg {
    override def toString() = "< " + msg
  }
  case class Out(msg: String) extends Msg {
    override def toString() = "> " + msg
  }
}
/**
 * @author asmirnov
 *
 */
class Console(printer: PrinterModel,settings: Settings) { console =>

  val buffer = ObservableBuffer.empty[Console.Msg]
  val disabled = BooleanProperty(false)
  val debug = settings.debugOutput
  var history: List[String] = Nil

  val node: Node = {
    new VBox {
      vgrow = Priority.Always
      hgrow = Priority.Always
      padding = Insets(2)
      children = List(
          new CheckBox {
            margin = Insets(5)
            text = "Debud output"
            selected <==> debug
          },
        new ListView[Console.Msg](buffer) {
          vgrow = Priority.Always
          hgrow = Priority.Always
          editable = false
          selectionModel().selectionMode = SelectionMode.Single
          cellFactory_= { v =>
            new ListCell[Console.Msg] {
              val input = item map {
                case null => false
                case Console.In(_) => true
                case Console.Out(_) => false
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
            printer.sendLine(input.text(), CommandSource.Console)
            history = input.text() :: history
            currentHistory = 0
            input.clear()
            input.requestFocus()
          }
          val input = new TextField {
            hgrow = Priority.Always
            onAction = { ae: ActionEvent => send }
            disable <== console.disabled
          }
          children = List(
            input,
            new Button {
              text = "Send"
              onAction = { ae: ActionEvent => send }
              disable <== console.disabled
            })
        })
    }
  }

  def isConsoleSource(src: CommandSource) = debug.value || src == CommandSource.Console || src == CommandSource.Unknown
  def addInput(src: CommandSource, line: String) {
    if (isConsoleSource(src)) {
      buffer += Console.In(line)
    }
  }

  def addOutput(src: CommandSource, line: String) {
    if (isConsoleSource(src)) {
      buffer += Console.Out(line)
    }
  }

  disabled <== printer.connected.not
  printer.addReceiveListener(addInput)
  printer.addSendListener(addOutput)
}

