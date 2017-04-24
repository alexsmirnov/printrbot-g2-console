package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.scene.layout.HBox
import scalafx.beans.property.ObjectProperty
import java.io.File
import scalafx.scene.control.Label
import scalafx.beans.binding.StringBinding
import scalafx.scene.control.Button
import scalafx.event.ActionEvent
import scalafx.geometry.Pos
import scalafx.scene.layout.Priority
import scalafx.beans.property.BooleanProperty
import java.nio.charset.StandardCharsets
import scala.io.Codec

object Job {
  val G0Cmd = """^\s*G0(\D.*)""".r
  val G1Cmd = """^\s*G1(\D.*)""".r
  val MoveParams = """\s*([XYZABEF])(\d+\.?\d*)""".r
  case class Position(x: Float, y: Float, z: Float, extruder: Float, speed: Float) {
    def moveTo(move: Move) = new Position(x+move.x.getOrElse(0f),
                                          y+move.y.getOrElse(0f),
                                          z+move.z.getOrElse(0f),
                                          extruder+move.extruder.getOrElse(0f),
                                          move.speed.getOrElse(speed))
    def distance(next: Position) = {
      val dx = x-next.x
      val dy = y-next.y
      val dz = z-next.z
      math.sqrt(dx*dx+dy*dy+dz*dz)
    }
    def travelTime(next: Position) = distance(next)/speed
  }
  sealed trait Move {
    def x: Option[Float]
    def y: Option[Float]
    def z: Option[Float]
    def extruder: Option[Float]
    def speed: Option[Float]
  }
  case class G0Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float]) extends Move
  object G0Move {
    def apply(p: Map[Char,Float]) = new G0Move(p.get('X'),p.get('Y'),p.get('Z'),p.get('E').orElse(p.get('A')),p.get('F'))
  }
  case class G1Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float]) extends Move
  object G1Move {
    def apply(p: Map[Char,Float]) = new G1Move(p.get('X'),p.get('Y'),p.get('Z'),p.get('E').orElse(p.get('A')),p.get('F'))
  }
}

class Job(printer: PrinterModel, settings: Settings) {


  val openGcodeDialog = new FileChooser {
    title = "Open Gcode File"
    extensionFilters ++= Seq(
      new ExtensionFilter("Gcode Files", "*.gcode"),
      new ExtensionFilter("All Files", "*.*"))
  }

  val gcodeFile = ObjectProperty[Option[File]](None)
  val noFile = BooleanProperty(true)
  noFile <== gcodeFile.map(_.isEmpty)

  val node: Node = new BorderPane {
    top = new HBox {
      hgrow = Priority.Always
      children = List(
        new Label("File name:"),
        new Label {
          minWidth = 100
          text <== gcodeFile.map[String, StringBinding] { _.map(_.getName).getOrElse("") }
        },
        new Button("Open") {
          alignment = Pos.BaselineRight
          onAction = { ae: ActionEvent => gcodeFile.update(selectFile()) }
        })
    }
  }

  def selectFile() = Option(openGcodeDialog.showOpenDialog(node.scene().window()))

  def processFile(file: File) {
    val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
  }
}