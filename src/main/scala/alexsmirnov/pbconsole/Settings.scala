package alexsmirnov.pbconsole

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.DoubleProperty

class Settings {
  val debugOutput = BooleanProperty(false)
  val bedWidth = DoubleProperty(110)
  val bedDepth = DoubleProperty(203)
  val height = DoubleProperty(130)
}