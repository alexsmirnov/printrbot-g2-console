package alexsmirnov.pbconsole.print

import java.io.File

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.ObjectProperty
import java.time.LocalDate
import java.time.LocalTime
import scalafx.scene.canvas.Canvas

object JobModel {
  
}
class JobModel {
  import Job._
  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[PrintStats](ZeroStats)
  val noFile = BooleanProperty(true)

  noFile <== gcodeFile.map{fo => fo.isEmpty}
  val jobActive = BooleanProperty(false)
  val jobPaused = BooleanProperty(false)
  val jobStartTime = ObjectProperty[LocalTime](LocalTime.now())
  val jobStats = ObjectProperty[PrintStats](ZeroStats)
  
  def updateFile(file: File,callback: Position => Unit) {}
  def start() {}
  def cancel() {}
  def pause() {}
  def resume() {}
}