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
import scalafx.scene.control.ButtonBar
import scalafx.scene.layout.Region
import scalafx.scene.layout.VBox
import scalafx.scene.control.ProgressBar

object Job {
  val LOG = Logger.getLogger("alexsmirnov.pbconsole.print.Job")

  val openGcodeDialog = new FileChooser {
    title = "Open Gcode File"
    extensionFilters ++= Seq(
      new ExtensionFilter("Gcode Files", "*.gcode"),
      new ExtensionFilter("All Files", "*.*"))
  }

}

class Job(job: JobModel, settings: Settings) {

  val stats = {
    val grid = new GridPane {
      padding = Insets(18)
      gridLinesVisible = true
    }
    grid.addRow(0, new Separator(), statLabel("Size"), statLabel("Min"), statLabel("Max"))
    grid.addRow(1, statRange("X", job.fileStats.map { s => s.x }): _*)
    grid.addRow(2, statRange("Y", job.fileStats.map { s => s.y }): _*)
    grid.addRow(3, statRange("Z", job.fileStats.map { s => s.z }): _*)
    grid.addRow(5, statLabel("Filanment"), floatOut(job.fileStats.map { s => s.extrude }))
    grid.addRow(6, statLabel("Time"), floatOut(job.fileStats.map { s => s.printTimeMinutes }))
    grid
  }

  val canvas = new Canvas() {
    scaleX = 2.0
    scaleY = 2.0
    width <== settings.bedWidth
    height <== settings.bedDepth
  }

  val bedImage = new StackPane {
    padding = Insets(10)
    children = List(
      new Rectangle {
        scaleX = 2.0
        scaleY = 2.0
        width <== settings.bedWidth
        height <== settings.bedDepth
        fill = Color.Aqua
      },
      canvas)
  }

  val printStatus = {

    val grid = new GridPane {
      padding = Insets(18)
      gridLinesVisible = true
      visible <== job.jobActive
    }
    grid.addRow(0, statLabel("Started:"))
    grid.addRow(0, statLabel("Remaining time:"))
    grid.addRow(0, statLabel("Current height:"))
    grid
  }

  val node: Node = new BorderPane {
    top = new HBox {
      hgrow = Priority.Always
      children = List(
        new ButtonBar {
          buttons = List(new Button("Open") {
            onAction = { ae: ActionEvent => selectFile() }
            disable <== job.jobActive
          }, new Button() {
            text <== when(job.jobActive) choose ("Cancel Print") otherwise ("Print")
            disable <== job.noFile or job.disconnected
            onAction = { ae: ActionEvent => if (job.jobActive()) job.cancel() else job.start() }
          }, new Button() {
            text <== when(job.jobPaused) choose ("Continue") otherwise ("Pause")
            disable <== job.jobActive.not()
            onAction = { ae: ActionEvent => if (job.jobPaused()) job.resume() else job.pause() }
          })
        },
        new Region {
          hgrow = Priority.Always
        },
        new Label("File :"),
        new Separator(),
        new Label {
          alignment = Pos.BaselineRight
          minWidth = 100
          text <== job.gcodeFile.map { _.map(_.getName).getOrElse("") }
        },
        new Separator())
    }
    right = new VBox(stats, printStatus)
    center = bedImage
    bottom = new HBox {
      visible <== job.jobActive
      hgrow = Priority.Always
      children = List(
        new ProgressBar {
          hgrow = Priority.Always
          maxWidth = 400
          progress <== job.printService.progress
        })
    }
  }

  def statLabel(txt: String) = new Label(txt)
  def floatOut(value: NumberBinding): Node = new Text() {
    text <== value.map { r => "%6.2f".format(r) }
  }

  def statRange(lbl: String, range: ObjectBinding[GCode.range]): Seq[javafx.scene.Node] = {
    val min = floatOut(range.map { _.min })
    val max = floatOut(range.map { _.max })
    val size = floatOut(range.map { _.size })
    Seq(statLabel(lbl), size, min, max)
  }

  def selectFile() {
    val selected = Job.openGcodeDialog.showOpenDialog(node.scene().window())
    if (null != selected) {
      val gc = canvas.graphicsContext2D
      gc.clearRect(0, 0, canvas.width(), canvas.height())
      gc.lineWidth = 1.0
      gc.stroke = Color.Red
      var lastPos = GCode.UnknownPosition
      job.updateFile(selected, { (cmd, pos) =>
        val lastXy = lastPos.x.zip(lastPos.y)
        val xy = pos.x.zip(pos.y)
        cmd match {
          case g1Move: GCode.G1Move if g1Move.extruder.isDefined =>
            lastXy.zip(xy).foreach {
              case ((x0, y0), (x, y)) => gc.strokeLine(x0, y0, x, y)
            }
          case move: GCode.Move =>
            xy.foreach {
              case (x, y) => gc.moveTo(x, y)
            }
          case cmd =>
        }
        lastPos = pos
      })
    }
  }
}