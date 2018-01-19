package alexsmirnov.pbconsole.print

import java.io.File
import java.time.LocalTime
import org.slf4j.LoggerFactory

import scala.io.Codec
import scala.sys.process._

import alexsmirnov.pbconsole.Macro
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.gcode.GCode
import javafx.concurrent.{ Service => JService, Task => JTask, Worker => JWorker }
import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.scene.media.AudioClip

object JobModel {
  trait FileProcessListener {
    def callback(): ((GCode, GCode.Position) => Unit)
  }
  lazy val pauseNotification = new AudioClip(this.getClass.getResource("/serious-strike.mp3").toExternalForm()){
//    cycleCount = 10
  }
}
class JobModel(printer: PrinterModel, settings: Settings) {
  import alexsmirnov.pbconsole.gcode.GCode._
  val LOG = LoggerFactory.getLogger("alexsmirnov.pbconsole.print.JobModel")
  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[PrintStats](ZeroStats)
  val noFile = BooleanProperty(true)

  noFile <== gcodeFile.map { fo => fo.isEmpty }
  val disconnected = printer.connected.not()
  val jobActive = BooleanProperty(false)
  val jobPaused = BooleanProperty(false)
  val jobStartTime = ObjectProperty[LocalTime](LocalTime.now())
  val jobStats = ObjectProperty[PrintStats](ZeroStats)
  val stopAtZpoints = ObservableBuffer.empty[Float]
  private[this] var fileListeners: List[JobModel.FileProcessListener] = Nil
  def addFileListener(listener: JobModel.FileProcessListener) {
    fileListeners = listener :: fileListeners
  }
  gcodeFile.onInvalidate {
    val callbacks = fileListeners.map(_.callback())
    gcodeFile().foreach { file =>
      val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
      try {
        val stats = GCode.processProgram(src.getLines()).foldLeft(ZeroStats) {
          case (_, (cmd, stat)) =>
            callbacks.foreach(_(cmd, stat.currentPosition))
            stat
        }
        fileStats.update(stats)
      } finally {
        src.close()
      }
    }
  }
  def updateFile(file: File) {
    gcodeFile.update(Some(file))
  }

  /**
   * Job cancel flag, to avoid thread interruption in stream
   */
  @volatile
  private[this] var jobCancelled: Boolean = true
  @volatile
  private[this] var paused: Boolean = false

  val printService = new JService[PrintStats] { srv =>
    override def createTask() = new JTask[PrintStats] { task =>
      // Check if print job is not cancelled
      def isActive(): Boolean = !(jobCancelled || task.isCancelled())
      def print(cmd: GCode) {
        if (isActive()) {
          cmd match {
            // M0 pause command
            case MCommand(0,_,_) => 
                      runInFxThread({pause();JobModel.pauseNotification.play()})
                      paused = true
            // wait for extruder temperature in loop, to allow monitoring and cancel
            case ExtTempAndWaitCommand(t) =>
              printer.print(ExtTempCommand(t))
              while (printer.extruder.temperature() < (t - 1.0) && isActive()) Thread.sleep(500)
            // wait for bed temperature in loop, to allow monitoring and cancel
            case BedTempAndWaitCommand(t) =>
              printer.print(BedTempCommand(t))
              while (printer.bed.temperature() < (t - 1.0) && isActive()) Thread.sleep(500)
            case _ => printer.print(cmd)
          }
        }
      }
      def call() = {
        // add 20 minutes for heatind and 10% for possible error
        val caffe = Process(Seq("caffeinate", "-i", "-t", ((jobStats().printTimeMinutes + 20) * 66).toInt.toString())).run()
        gcodeFile().foreach { file =>
          task.updateProgress(0.0, fileStats().printTimeMinutes)
          val src = scala.io.Source.fromFile(file)(Codec.ISO8859)
          // send header
          val lines = Macro.prepare(settings.jobStart(), settings) ++ src.getLines()
          val stopPoints = stopAtZpoints.iterator
          var nextStop = if (stopPoints.hasNext) Some(stopPoints.next()) else None
          try {
            LOG.info(s"Print job started")
            GCode.processProgram(lines).takeWhile { _ => isActive() }.foreach {
              case (cmd, currentStat) =>
                // check 'pause at Z status
                nextStop.foreach { z =>
                  currentStat.currentPosition.z.foreach { mz =>
                    if (mz >= z) {
                      runInFxThread({pause();JobModel.pauseNotification.play()})
                      paused = true
                      nextStop = if (stopPoints.hasNext) Some(stopPoints.next()) else None
                    }
                  }
                }
                print(cmd)
                task.updateProgress(currentStat.printTimeMinutes, fileStats().printTimeMinutes)
                task.updateValue(currentStat)
                // pause print
                if (paused) {
                  LOG.info(s"Print job paused")
                  val eTemp = printer.extruder.target
                  val curX = currentStat.currentPosition.x.getOrElse(0)
                  val curY = currentStat.currentPosition.y.getOrElse(0)
                  val curZ = currentStat.currentPosition.z.getOrElse(0)
                  Macro.prepare(settings.pauseStart(), settings).
                    foreach { line => print(GCode(line)) }
                  while (paused && isActive()) Thread.sleep(500)
                  // continue after pause
                  LOG.info(s"Print job resumed")
                  Macro.prepare(settings.pauseEnd(), settings,
                    "extruder" -> eTemp,
                    "X" -> curX, "Y" -> curY, "Z" -> curZ).
                    foreach { line => print(GCode(line)) }
                }
            }
          } catch {
            case ie: InterruptedException =>
              val ts = Thread.currentThread().isInterrupted()
              LOG.warn(s"Print tread interrupted. Thread interrupt state $ts")

          } finally {
            LOG.info(s"Print job completed, send final GCode")
            // send footer
            Macro.prepare(settings.jobEnd(), settings).foreach { line => printer.print(GCode(line)) }
            caffe.destroy()
            src.close()
            LOG.info(s"Print job finished")
          }
        }
        ZeroStats
      }
    }
  }

  jobActive <== (printService.state === JWorker.State.SCHEDULED) or (printService.state === JWorker.State.RUNNING)
  jobActive.onInvalidate(jobPaused.value= false)
  jobPaused.onInvalidate { paused = jobPaused() }

  val printStats = printService.value.map { ps => if (null == ps) ZeroStats else ps }

  def start() {
    if (gcodeFile().isDefined && !jobActive()) {
      printService.reset()
      jobCancelled = false
      paused = false
      printService.start()
    }
  }
  def cancel() {
    //    printService.cancel()
    jobCancelled = true
  }
  def pause() { jobPaused.value = true }
  def resume() { jobPaused.value = false }
}