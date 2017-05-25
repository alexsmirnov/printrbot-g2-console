package alexsmirnov.pbconsole.print

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
import scala.io.Codec
import scalafx.scene.control.Separator
import scalafx.scene.layout.GridPane
import scalafx.geometry.Insets
import alexsmirnov.pbconsole._
import scalafx.beans.binding.ObjectBinding
import scalafx.scene.text.Text
import javafx.beans.binding.FloatBinding
import scalafx.beans.binding.NumberBinding
import scalafx.scene.canvas.Canvas
import scalafx.scene.layout.StackPane
import scalafx.scene.shape.Rectangle
import scalafx.scene.paint.Color
import java.util.logging.Logger

object Job {
  val LOG = Logger.getLogger("alexsmirnov.pbconsole.print.Job")
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
    def size = max - min
  }

  case class PrintStats(x: range,
    y: range,
    z: range,
    extrude: Float,
    printTimeMinutes: Float)

  val EmptyStats = PrintStats(range(), range(), range(), 0f, 0f)
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

class Job(printer: PrinterModel, job: JobModel, settings: Settings) {

  val stats = {
    val grid = new GridPane {
      padding = Insets(18)
      gridLinesVisible = true
    }
    grid.addRow(0, new Separator(),statLabel("Size"),statLabel("Min"),statLabel("Max"))
    grid.addRow(1,statRange("X", job.fileStats.map { s => s.x }): _*)
    grid.addRow(2,statRange("Y", job.fileStats.map { s => s.y }): _*)
    grid.addRow(3,statRange("Z", job.fileStats.map { s => s.z }): _*)
    grid.addRow(5,statLabel("Filanment"), floatOut(job.fileStats.map { s => s.extrude }))
    grid.addRow(6,statLabel("Time"), floatOut(job.fileStats.map { s => s.printTimeMinutes }))
    grid
  }
  
  val canvas = new Canvas() {
            width <== settings.bedWidth.add(10)
            height <== settings.bedDepth.add(10)
  }
  
  val bedImage = new StackPane {
    padding = Insets(10)
    children = List(
          new Rectangle {
            width <== settings.bedWidth
            height <== settings.bedDepth
            fill = Color.Aqua
          },
          canvas
        )
  }

  val node: Node = new BorderPane {
    top = new HBox {
      hgrow = Priority.Always
      children = List(
        new Label("File name:"),
        new Separator(),
        new Label {
          minWidth = 100
          text <== job.gcodeFile.map { _.map(_.getName).getOrElse("") }
        },
        new Separator(),
        new Button("Open") {
          alignment = Pos.BaselineRight
          onAction = { ae: ActionEvent => job.gcodeFile.update(selectFile()) }
        })
    }
    right = stats
    center = bedImage
  }
  
  def statLabel(txt: String) = new Label(txt)
  def floatOut(value: NumberBinding): Node = new Text() {
    text <== value.map { r=>"%6.2f".format(r) }
  }
  
  def statRange(lbl: String,range: ObjectBinding[Job.range]): Seq[javafx.scene.Node] = {
    val min = floatOut(range.map { _.min })
    val max = floatOut(range.map { _.max })
    val size = floatOut(range.map { _.size })
    Seq(statLabel(lbl),size,min,max)
  }

  def selectFile() = Option(Job.openGcodeDialog.showOpenDialog(node.scene().window()))

  def processFile(file: File) {
    val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
    val gc = canvas.graphicsContext2D
    gc.clearRect(0, 0, canvas.width(), canvas.height())
    gc.lineWidth = 2.0
    gc.stroke = Color.Red
    var lastPos = Job.UnknownPosition
    val stats = Job.processProgram(src.getLines()){ (_,pos,_) =>
      if(lastPos.x.isDefined && lastPos.y.isDefined){
        pos.x.zip(pos.y).foreach{
          case (x,y) => gc.strokeLine(lastPos.x.get,lastPos.y.get,x, y)
        }
      } else {
        pos.x.zip(pos.y).foreach{
          case (x,y) => gc.moveTo(x, y)
        }
      }
      lastPos = pos
    }
    job.fileStats.update(stats)
  }
  job.gcodeFile.onInvalidate(job.gcodeFile().foreach(processFile(_)))
}