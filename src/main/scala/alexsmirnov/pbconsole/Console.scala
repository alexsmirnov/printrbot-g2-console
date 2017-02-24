package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.layout.{ VBox, HBox }
import scalafx.scene.layout.Priority
import scalafx.geometry.Insets
import scalafx.scene.control.{ ListView, Button, TextField }
import scalafx.scene.control.SelectionMode
import scalafx.scene.control.ListCell
import scalafx.beans.binding.Bindings
import scalafx.scene.paint.Color
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.beans.property.BooleanProperty
import scalafx.application.Platform
import scalafx.beans.binding.StringBinding

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
 * TODO: disable send if disconnected.
 * @author asmirnov
 *
 */
class Console { console =>

  val buffer = ObservableBuffer.empty[Console.Msg]
  var listener: Option[(String => Unit)] = None
  val enabled = BooleanProperty(false)

  val node: Node = {
    new VBox {
      vgrow = Priority.Always
      hgrow = Priority.Always
      padding = Insets(5)
      children = List(
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
          def send {
            listener.foreach(_(input.text()))
            input.clear()
            input.requestFocus()
          }
          val input = new TextField {
            hgrow = Priority.Always
            onAction = { ae: ActionEvent => send }
            disable <== console.enabled.not
          }
          children = List(
            input,
            new Button {
              text = "Send"
              onAction = { ae: ActionEvent => send }
              disable <== console.enabled.not
            })
        })
    }
  }

  val statusReport = """\{(sr\:|"sr"\:)""".r

  def addInput(line: String) {
    buffer += Console.In(line)
  }

  def addOutput(line: String) = buffer += Console.Out(line)

  def onAction(f: String => Unit) { listener = Some(f) }

  def bind(printer: PrinterModel) {
    enabled <== printer.connected
    onAction(printer.sendLine)
    printer.addReceiveListener({ 
      case sr: StatusReport => ()
      case r => addInput(r.rawLine)
    })
    printer.addSendListener(addOutput)
  }
}

