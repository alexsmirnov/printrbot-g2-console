package alexsmirnov.pbconsole

import alexsmirnov.pbconsole.gcode.GCode
import scalafx.Includes._
import scalafx.geometry.Orientation
import scalafx.scene.Node
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.input.ScrollEvent
import scalafx.scene.layout.VBox

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ZOffsetControl(printer: PrinterModel,  settings: Settings) {

  // Once connected, set saved Z offset

  printer.connected.onChange{(c,ov,nv) => if(nv) Future{Thread.sleep(100);changeOffset(settings.zOffset.value)}}

  private val slider: Slider = new Slider(-0.5, 0.5, settings.zOffset.value) {
    id = "zSlider"
    orientation = Orientation.Vertical
    showTickMarks = true
    showTickLabels = true
    majorTickUnit = 0.1
    minorTickCount = 3
    blockIncrement = 0.025
    snapToTicks = true
    value.onChange { (v,old,newVal) => if(!valueChanging.value) changeOffset(newVal.doubleValue())}
    value <==> settings.zOffset
    disable <== printer.connected.not()
  }


  val node: Node = new VBox {
    id = "zOffset"
    children = List(new Label("Z offset"), slider)
  }

  private def changeOffset(newVal: Double) = {
    // only send values rounded to 0.025
    printer.offer(offsetGcode(-newVal), CommandSource.Monitor)
  }

  private def offsetGcode(value: Double) = {
    GCode.parse(f"M206Z$value%2.3f")
  }
}
