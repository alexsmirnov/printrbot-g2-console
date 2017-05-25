package alexsmirnov.pbconsole.print

import java.io.File

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.ObjectProperty
class JobModel {
  import Job._
  val gcodeFile = ObjectProperty[Option[File]](None)
  val fileStats = ObjectProperty[PrintStats](PrintStats(range(0,0), range(0,0), range(0,0), 0f, 0L))
  val noFile = BooleanProperty(true)

  noFile <== gcodeFile.map{fo => fo.isEmpty}
  val jobActive = BooleanProperty(false)
  val jobPaused = BooleanProperty(false)
}