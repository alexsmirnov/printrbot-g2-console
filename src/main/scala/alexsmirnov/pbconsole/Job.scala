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
  val G92Cmd = """^\s*G92(\D.*)""".r
  val MoveParams = """\s*([XYZABEF])(\d+\.?\d*)""".r
  val NoComment = """^\s+([^;]+)\s*;?.*$""".r

  def dist(from: Option[Float], to: Option[Float]): Float = from.flatMap { f => to.map(_ - f) }.getOrElse(0.0f)
  case class Position(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Float) {
    def moveTo(move: Move) = new Position(
      move.x.orElse(x),
      move.y.orElse(y),
      move.z.orElse(z),
      move.extruder.orElse(extruder),
      move.speed.getOrElse(speed))
    def distance(next: Position) = {
      val dx = dist(x, next.x)
      val dy = dist(y, next.y)
      val dz = dist(z, next.z)
      math.sqrt(dx * dx + dy * dy + dz * dz).toFloat
    }
    def travelTime(next: Position) = {
      val d = distance(next)
      if (d > 0) d / next.speed else dist(extruder, next.extruder) / next.speed
    }
  }

  val UnknownPosition = Position(None, None, None, None, 1.0f)
  sealed trait GCode {

  }

  sealed trait Move {
    def x: Option[Float]
    def y: Option[Float]
    def z: Option[Float]
    def extruder: Option[Float]
    def speed: Option[Float]
  }
  case class G0Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float]) extends Move with GCode
  object G0Move {
    def apply(p: Map[Char, Float]) = new G0Move(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')), p.get('F'))
  }
  case class G1Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float]) extends Move with GCode
  object G1Move {
    def apply(p: Map[Char, Float]) = new G1Move(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')), p.get('F'))
  }
  case class G92SetPos(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float]) extends Move with GCode {
    val speed = None
  }
  object G92SetPos {
    def apply(p: Map[Char, Float]) = new G92SetPos(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')))
  }
  case class GCommand(command: String) extends GCode
  case object EmptyCommand extends GCode

  def parseParams(params: String) = MoveParams.findAllMatchIn(params).map { m => m.subgroups(0).head -> (m.subgroups(1).toFloat) }.toMap

  def parse(line: String): GCode = line match {
    case "" => EmptyCommand
    case G0Cmd(params) => G0Move(parseParams(params))
    case G1Cmd(params) => G1Move(parseParams(params))
    case G92Cmd(params) => G92SetPos(parseParams(params))
    case other => GCommand(other)
  }

  case class range(min: Float = Float.MaxValue, max: Float = Float.MinValue) {
    def update(value: Option[Float]) = value map { v =>
      val newMin = min.min(v)
      val newMax = max.max(v)
      range(newMin, newMax)
    } getOrElse (this)
  }

  case class PrintStats(x: range,
    y: range,
    z: range,
    extrude: Float,
    printTimeSec: Float)

  val EmptyStats = PrintStats(range(), range(), range(), 0f, 0L)
  def estimatePrint(lines: Iterator[String]) = processProgram(lines) { (_, _, _) => () }
  def processProgram(lines: Iterator[String])(callback: (String, Position, PrintStats) => Unit): PrintStats = {
    lines.foldLeft((EmptyStats, UnknownPosition)) { (stp, line) =>
      val strip = NoComment.findFirstMatchIn(line).map(_.subgroups(0)).getOrElse("")
      val (currentStats, currentPosition) = stp
      parse(line) match {
        case EmptyCommand => stp
        case move: G92SetPos =>
          val nextPosition = currentPosition.moveTo(move)
          callback(strip, nextPosition, currentStats)
          (currentStats, nextPosition)
        case move: Move =>
          val nextPosition = currentPosition.moveTo(move)
          val PrintStats(rangeX, rangeY, rangeZ, ex, time) = currentStats
          val nextStats = PrintStats(rangeX.update(nextPosition.x),
            rangeY.update(nextPosition.y),
            rangeZ.update(nextPosition.z),
            ex + dist(currentPosition.extruder, nextPosition.extruder),
            time + currentPosition.travelTime(nextPosition))
          callback(strip, nextPosition, nextStats)
          (nextStats, nextPosition)
        case other =>
          callback(strip, currentPosition, currentStats)
          stp
      }
    }._1
  }

  val openGcodeDialog = new FileChooser {
    title = "Open Gcode File"
    extensionFilters ++= Seq(
      new ExtensionFilter("Gcode Files", "*.gcode"),
      new ExtensionFilter("All Files", "*.*"))
  }

}

class Job(printer: PrinterModel, settings: Settings) {

  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[Job.PrintStats](Job.EmptyStats)
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

  def selectFile() = Option(Job.openGcodeDialog.showOpenDialog(node.scene().window()))

  def processFile(file: File) {
    val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
    val stats = Job.estimatePrint(src.getLines())
    fileStats.update(stats)
  }
  gcodeFile.onInvalidate(gcodeFile().foreach(processFile(_)))
}