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
import java.util.logging.Logger
import alexsmirnov.pbconsole.gcode.GCode

object JobModel {
  trait FileProcessListener {
    def callback(): ((GCode, GCode.Position) => Unit)
  }
}
class JobModel(printer: PrinterModel, settings: Settings) {
  import GCode._
  val LOG = Logger.getLogger("alexsmirnov.pbconsole.print.JobModel")
  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[PrintStats](ZeroStats)
  val noFile = BooleanProperty(true)

  noFile <== gcodeFile.map { fo => fo.isEmpty }
  val disconnected = printer.connected.not()
  val jobActive = BooleanProperty(false)
  val jobPaused = BooleanProperty(false)
  val jobStartTime = ObjectProperty[LocalTime](LocalTime.now())
  val jobStats = ObjectProperty[PrintStats](ZeroStats)
  private[this] var fileListeners: List[JobModel.FileProcessListener] = Nil
  def addFileListener(listener: JobModel.FileProcessListener) {
    fileListeners = listener :: fileListeners
  }
  gcodeFile.onInvalidate {
    val callbacks = fileListeners.map(_.callback())
    gcodeFile().foreach { file =>
      val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
      val stats = GCode.processProgram(src.getLines()) { (cmd, pos, _) => callbacks.foreach(_(cmd, pos)) }
      fileStats.update(stats)
    }
  }
  def updateFile(file: File) {
    gcodeFile.update(Some(file))
  }

 /**
 * Job cancel flag, to avoid thread interruption in streams
 */
  @volatile 
  private[this] var jobCancelled: Boolean = true

  val printService = new JService[PrintStats] { srv =>
    override def createTask() = new JTask[PrintStats] { task =>
      // Check if print job is not cancelled
      def isActive(): Boolean = !(jobCancelled || task.isCancelled())
      def call() = {
        // add 20 minutes for heatind and 10% for possible error
        val caffe = Process(Seq("caffeinate", "-i", "-t", ((jobStats().printTimeMinutes + 20) * 66).toInt.toString())).run()
        gcodeFile().foreach { file =>
          task.updateProgress(0.0, fileStats().printTimeMinutes)
          val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
          // send header
          val lines = Macro.prepare(settings.jobStart(), settings) ++ src.getLines()
          try {
            LOG.info(s"Print job started")
            GCode.processProgram(lines) { (cmd, pos, currentStat) =>
              if (isActive()) {
                cmd match {
                  case ExtTempAndWaitCommand(t) =>
                    printer.sendLine(ExtTempCommand(t).line, CommandSource.Job)
                    while (printer.extruder.temperature() < (t - 1.0) && isActive()) Thread.sleep(500)
                  case BedTempAndWaitCommand(t) =>
                    printer.sendLine(BedTempCommand(t).line, CommandSource.Job)
                    while (printer.bed.temperature() < (t - 1.0) && isActive()) Thread.sleep(500)
                  case _ => printer.sendLine(cmd.line, CommandSource.Job)
                }
                task.updateProgress(currentStat.printTimeMinutes, fileStats().printTimeMinutes)
                task.updateValue(currentStat)
              }
            }
          } catch {
            case ie: InterruptedException =>
              val ts = Thread.currentThread().isInterrupted()
              LOG.warning(s"Print tread interrupted. Thread interrupt state $ts")

          } finally {
            LOG.info(s"Print job completed, send final GCode")
            caffe.destroy()
            // send footer
            Macro.prepare(settings.jobEnd(), settings).foreach(printer.sendLine(_, CommandSource.Job))
            LOG.info(s"Print job finished")
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
      jobCancelled = false
      printService.start()
    }
  }
  def cancel() {
    //    printService.cancel()
    jobCancelled = true
  }
  def pause() {}
  def resume() {}
}