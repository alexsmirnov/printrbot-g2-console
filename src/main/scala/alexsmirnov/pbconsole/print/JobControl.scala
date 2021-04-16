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
import org.slf4j.LoggerFactory
import scalafx.scene.control.ButtonBar
import scalafx.scene.layout.Region
import scalafx.scene.layout.VBox
import scalafx.scene.control.ProgressBar
import alexsmirnov.pbconsole.gcode.GCode
import scalafx.scene.control.ListView
import scalafx.scene.control.cell.TextFieldListCell
import scalafx.util.converter.FloatStringConverter
import scalafx.scene.control.SelectionModel
import scalafx.scene.control.SelectionMode

object JobControl {
  val LOG = LoggerFactory.getLogger("alexsmirnov.pbconsole.print.Job")

  val openGcodeDialog = new FileChooser {
    title = "Open Gcode File"
    extensionFilters ++= Seq(
      new ExtensionFilter("Gcode Files", "*.gcode"),
      new ExtensionFilter("All Files", "*.*"))
  }

}

class JobControl(job: JobModel, settings: Settings) {

  val stats = {
    val grid = new GridPane {
      id = "job_stats"
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
    id = "job_canvas"
    width <== settings.bedWidth * 2.0
    height <== settings.bedDepth * 2.0
  }

  val bedImage = new StackPane {
    id = "job_image"
    children = List(
      new Rectangle {
        id = "job_image_backgroud"
        width <== settings.bedWidth * 2.0
        height <== settings.bedDepth * 2.0
      },
      canvas)
  }

  val printStatus = {

    val grid = new GridPane {
      id = "job_status"
      gridLinesVisible = true
      visible <== job.jobActive
    }
    grid.addRow(0, statLabel("Started:"))
    grid.addRow(1, statLabel("Remaining time:"),
      floatOut(job.fileStats.map { s => s.printTimeMinutes } - job.printStats.map { js => js.printTimeMinutes }))
    grid.addRow(2, statLabel("Current height:"),
      floatOut(job.printStats.map { js => js.currentPosition.z.getOrElse(0f) }))
    grid
  }

  val stopAtZlevels: Node = {
    val list = new ListView[Float] {
      id="stopAtZlist"
      cellFactory = TextFieldListCell.forListView(new FloatStringConverter)
      items = job.stopAtZpoints
      editable = true
      selectionModel().selectionMode = SelectionMode.Single
    }
    new VBox {
      id="stopAtZ"
      children = List(
        list,
        new ButtonBar {
          buttons = List(new Button("+") {
          onAction = { ae: ActionEvent => job.stopAtZpoints.add(0.0F) }

          },new Button("-") {
          onAction = { ae: ActionEvent => list.selectionModel().getSelectedIndices.foreach(job.stopAtZpoints.remove(_)) }
          })
        })
    }
  }

  val node: Node = new BorderPane {
    id = "job_control"
    top = new HBox {
      id = "job_file"
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
    right = new VBox(stats, stopAtZlevels, printStatus) {
      id = "job_stats"
    }
    center = bedImage
    bottom = new HBox {
      id = "job_progress"
      visible <== job.jobActive
      hgrow = Priority.Always
      children = List(
        new ProgressBar {
          id = "job_progress_bar"
          hgrow = Priority.Always
          maxWidth = 400
          progress <== job.printService.progress
        })
    }
  }

  def statLabel(txt: String) = {
    val lbl = new Label(txt)
    lbl.styleClass += "job_label"
    lbl
  }
  def floatOut(value: NumberBinding): Node = new Text() {
    styleClass += "job_output_text";
    text <== value.map { r => "%6.2f".format(r) }
  }

  def statRange(lbl: String, range: ObjectBinding[GCode.range]): Seq[javafx.scene.Node] = {
    val min = floatOut(range.map { _.min })
    val max = floatOut(range.map { _.max })
    val size = floatOut(range.map { _.size })
    Seq(statLabel(lbl), size, min, max)
  }

  def selectFile() {
    val selected = JobControl.openGcodeDialog.showOpenDialog(node.scene().window())
    if (null != selected) {
      job.updateFile(selected)
    }
  }

  job.addFileListener(new JobModel.FileProcessListener {
    def callback() = {
      val gc = canvas.graphicsContext2D
      val height = canvas.height()
      gc.clearRect(0, 0, canvas.width(), height)
      gc.lineWidth = 1.0
      gc.stroke = Color.Red
      var lastPos = GCode.UnknownPosition
      val cb = { (cmd: GCode, pos: GCode.Position) =>
        val lastXy = lastPos.x.zip(lastPos.y)
        val xy = pos.x.zip(pos.y)
        cmd match {
          case g1Move: GCode.G1Move if g1Move.extruder.isDefined =>
            lastXy.zip(xy).foreach {
              case ((x0, y0), (x, y)) => gc.strokeLine(x0 * 2, height-y0 * 2, x * 2, height-y * 2)
            }
          case move: GCode.Move =>
            xy.foreach {
              case (x, y) => gc.moveTo(x * 2, height-y * 2)
            }
          case cmd =>
        }
        lastPos = pos
      }
      cb
    }
  })
}