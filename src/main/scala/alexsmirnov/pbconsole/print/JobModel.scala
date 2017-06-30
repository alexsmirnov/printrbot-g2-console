package alexsmirnov.pbconsole.print

import java.io.File
import scala.sys.process._

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
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.Macro

object JobModel {

}
class JobModel(printer: PrinterModel, settings: Settings) {
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

  val printService = new JService[PrintStats] { srv =>
    override def createTask() = new JTask[PrintStats] { task =>
      def call() = {
        // add 20 minutes for heatind and 10% for possible error
        Process(Seq("caffeinate", "-i", "-t", ((jobStats().printTimeMinutes+20) * 66).toInt.toString())).run()
        gcodeFile().foreach { file =>
          task.updateProgress(0.0, fileStats().printTimeMinutes)
          val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
          // send header
          val lines = Macro.prepare(settings.jobStart(), settings) ++ src.getLines()
          try {
            GCode.processProgram(lines) { (cmd, pos, currentStat) =>
              if (!task.isCancelled()) {
                cmd match {
                  case ExtTempAndWaitCommand(t) =>
                    printer.sendLine(ExtTempCommand(t).command, CommandSource.Job)
                    while (printer.extruder.temperature() < (t - 1.0) && !task.isCancelled()) Thread.sleep(500)
                  case BedTempAndWaitCommand(t) =>
                    printer.sendLine(BedTempCommand(t).command, CommandSource.Job)
                    while (printer.bed.temperature() < (t - 1.0) && !task.isCancelled()) Thread.sleep(500)
                  case _ => printer.sendLine(cmd.command, CommandSource.Job)
                }
                task.updateProgress(currentStat.printTimeMinutes, fileStats().printTimeMinutes)
                task.updateValue(currentStat)
              }
            }
          } finally {
            // send footer
            Macro.prepare(settings.jobEnd(), settings).foreach(printer.sendLine(_, CommandSource.Job))
          }
        }
        ZeroStats
      }
    }
  }
  
  jobActive <== (printService.state === JWorker.State.SCHEDULED) or (printService.state === JWorker.State.RUNNING)

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