package alexsmirnov.pbconsole.print

import java.io.File

import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.ObjectProperty
import java.time.LocalDate
import java.time.LocalTime
import scalafx.scene.canvas.Canvas
import alexsmirnov.pbconsole.PrinterModel
import scala.io.Codec
import javafx.concurrent.{ Service => JService, Task => JTask, Worker => JWorker }
import scalafx.concurrent.Service
import scalafx.concurrent.Task
import alexsmirnov.pbconsole.CommandSource
import scalafx.concurrent.WorkerStateEvent

object JobModel {

}
class JobModel(printer: PrinterModel) {
  import GCode._
  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[PrintStats](ZeroStats)
  val noFile = BooleanProperty(true)

  noFile <== gcodeFile.map { fo => fo.isEmpty }
  val disconnected = printer.connected.not()
  val jobActive = BooleanProperty(false)
  val jobPaused = BooleanProperty(false)
  val jobStartTime = ObjectProperty[LocalTime](LocalTime.now())
  val jobStats = ObjectProperty[PrintStats](ZeroStats)

  def updateFile(file: File, callback: (GCode, Position) => Unit) {
    gcodeFile.update(Some(file))
    val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
    val stats = GCode.processProgram(src.getLines()) { (cmd, pos, _) => callback(cmd, pos) }
    fileStats.update(stats)
  }

  val printService = new JService[Unit] { srv =>
    override def createTask() = new JTask[Unit] { task =>
      def call() = {
        gcodeFile().foreach { file =>
          val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
          // TODO - send header
          GCode.processProgram(src.getLines()) { (cmd, pos, currentStat) =>
            if (!task.isCancelled()) {
              printer.sendLine(cmd.command, CommandSource.Job)
              task.updateProgress(currentStat.printTimeMinutes, fileStats().printTimeMinutes)
            }
          }
          // TODO - send footer
        }
      }
    }
  }
  jobActive <== ( printService.state === JWorker.State.SCHEDULED ) or ( printService.state === JWorker.State.RUNNING )
  
  def start() {
    if (gcodeFile().isDefined && !jobActive()) {
      printService.reset()
      printService.start()
    }
  }
  def cancel() {
    printService.cancel()
  }
  def pause() {}
  def resume() {}
}