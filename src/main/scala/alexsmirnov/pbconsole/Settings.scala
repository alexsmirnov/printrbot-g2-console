package alexsmirnov.pbconsole

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.DoubleProperty
import scalafx.collections.ObservableBuffer
import scalafx.beans.property.StringProperty
import java.util.prefs.Preferences
import javafx.collections.FXCollections
import java.io.File

class Settings {
  val debugOutput = BooleanProperty(false)
  val bedWidth = DoubleProperty(110)
  val bedDepth = DoubleProperty(203)
  val height = DoubleProperty(130)
  val zOffset = DoubleProperty(0.0)
  val macros = new ObservableBuffer(FXCollections.observableArrayList(Macro.extractor))
  val jobStart = StringProperty("")
  val jobEnd = StringProperty("")
  val uploadFolder = StringProperty("")
  val joggerInterval = DoubleProperty(10)
  val jogXYstep = DoubleProperty(1)
  val jogXYspeed = DoubleProperty(100)
  val jogZspeed = DoubleProperty(100)
  val jogZstep = DoubleProperty(1)
  val jogEstep = DoubleProperty(1)
  val jogEspeed = DoubleProperty(100)
}

object Settings {
  val DO = "debugOutput"
  val BED_W = "bedWidth"
  val BED_D = "bedDepth"
  val H = "height"
  val Z_OFFSET = "zOffset"
  val NUM_MACROS = "numMacros"
  val JS = "jobStart"
  val JE = "jobEnd"
  val UF = "upload"
  val JOG_XY_STEP = "joggerXYstep"
  val JOG_XY_SPEED = "joggerXYspeed"
  val JOG_Z_STEP = "joggerZstep"
  val JOG_Z_SPEED = "joggerZspeed"
  val JOG_E_STEP = "joggerEstep"
  val JOG_E_SPEED = "joggerEspeed"
  val JOG_INTERVAL = "joggerInterval"
  def apply(path: String): Settings = {
    val s = restore(path)
    bind(s, path)
    s
  }
  def restore(path: String): Settings = {
    val node = Preferences.userRoot().node(path)
    def restoreDouble(prop: DoubleProperty,key: String, default: Double) {
      prop.update(node.getDouble(key, default))
    }
    val s = new Settings
    s.debugOutput.update(node.getBoolean(DO, false))
    restoreDouble(s.bedWidth,BED_W, 100)
    restoreDouble(s.bedDepth,BED_D, 203)
    restoreDouble(s.height,H, 130)
    restoreDouble(s.zOffset,Z_OFFSET, 0.0)
    restoreDouble(s.joggerInterval,JOG_INTERVAL, 5.0)
    restoreDouble(s.jogXYspeed,JOG_XY_SPEED, 2000.0)
    restoreDouble(s.jogXYstep,JOG_XY_STEP, 0.2)
    restoreDouble(s.jogZspeed,JOG_Z_SPEED, 1500.0)
    restoreDouble(s.jogZstep,JOG_Z_STEP, 0.2)
    restoreDouble(s.jogEspeed,JOG_E_SPEED, 700.0)
    restoreDouble(s.jogEstep,JOG_E_STEP, 0.1)
    val uploadFolder = node.get(UF,"")
    if(uploadFolder.isEmpty()) {
      val userHome = System.getProperty("user.dir")
      val defaultFolder = new File(new File(new File(userHome),".pbconsole"),"upload").getAbsolutePath
      s.uploadFolder.update(defaultFolder)
    } else s.uploadFolder.update(uploadFolder)
    val nMacros = node.getInt(NUM_MACROS, 0)
    ( 0 until nMacros ) foreach { n =>
      val name =node.get("mName"+n, "")
      if( !name.isEmpty() ) {
        val m = Macro(name,node.get("mDescription"+n, ""),node.get("mContent"+n, ""))
        s.macros.add(m)
      }
    }
    s.jobStart.update(node.get(JS,""))
    s.jobEnd.update(node.get(JE,""))
    s
  }
  private def bindString(node: Preferences,property: StringProperty,key: String) {
    property.onChange{ node.put(key, property());node.flush() }
  }
  private def bindDouble(node: Preferences,property: DoubleProperty,key: String) {
    property.onChange{ node.putDouble(key, property());node.flush()  }
  }
  private def bindBoolean(node: Preferences,property: BooleanProperty,key: String) {
    property.onChange{ node.putBoolean(key, property());node.flush()  }
  }
  def bind(settings: Settings,path: String) {
    val node = Preferences.userRoot().node(path)
    bindBoolean(node, settings.debugOutput, DO)
    bindDouble(node, settings.bedWidth, BED_W)
    bindDouble(node, settings.bedDepth, BED_D)
    bindDouble(node, settings.height, H)
    bindDouble(node, settings.zOffset, Z_OFFSET)
    bindString(node, settings.jobStart, JS)
    bindString(node, settings.jobEnd, JE)
    bindString(node, settings.uploadFolder, UF)
    bindDouble(node,settings.joggerInterval,JOG_INTERVAL)
    bindDouble(node,settings.jogXYspeed,JOG_XY_SPEED)
    bindDouble(node,settings.jogXYstep,JOG_XY_STEP)
    bindDouble(node,settings.jogZspeed,JOG_Z_SPEED)
    bindDouble(node,settings.jogZstep,JOG_Z_STEP)
    bindDouble(node,settings.jogEspeed,JOG_E_SPEED)
    bindDouble(node,settings.jogEstep,JOG_E_STEP)
    settings.macros.onChange {
      val mWithIndex = settings.macros.zipWithIndex
      node.putInt(NUM_MACROS, mWithIndex.size)
      mWithIndex.foreach {
        case (m,n) =>
          node.put("mName"+n,m.name)
          node.put("mDescription"+n,m.description)
          node.put("mContent"+n,m.content)
      }
      node.flush() 
    }
  }
}