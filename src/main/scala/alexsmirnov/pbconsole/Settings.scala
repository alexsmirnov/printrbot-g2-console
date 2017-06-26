package alexsmirnov.pbconsole

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.DoubleProperty
import scalafx.collections.ObservableBuffer
import scalafx.beans.property.StringProperty
import java.util.prefs.Preferences

class Settings {
  val debugOutput = BooleanProperty(false)
  val bedWidth = DoubleProperty(110)
  val bedDepth = DoubleProperty(203)
  val height = DoubleProperty(130)
  val zOffset = DoubleProperty(0.0)
  val macros = ObservableBuffer.empty[Macro]
  val jobStart = StringProperty("")
  val jobEnd = StringProperty("")
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
  def apply(path: String): Settings = {
    val s = restore(path)
    bind(s, path)
    s
  }
  def restore(path: String): Settings = {
    val node = Preferences.userRoot().node(path)
    val s = new Settings
    s.debugOutput.update(node.getBoolean(DO, false))
    s.bedWidth.update(node.getDouble(BED_W, 100))
    s.bedDepth.update(node.getDouble(BED_D, 203))
    s.height.update(node.getDouble(H, 130))
    s.zOffset.update(node.getDouble(Z_OFFSET, 0.0))
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
    settings.macros.onChange {
      val mWithIndex = settings.macros.zipWithIndex
      node.putInt(NUM_MACROS, mWithIndex.size)
      mWithIndex.foreach {
        case (Macro(name,description,content),n) =>
          node.put("mName"+n,name)
          node.put("mDescription"+n,description)
          node.put("mContent"+n,content)
      }
      node.flush() 
    }
  }
}