package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.DoubleProperty

class BindingOpsTest {
 val bp = BooleanProperty(false) 
 val dp = DoubleProperty(0.0)
 val db = bp.map{v => if(v) 1.0 else 2.0}
 dp <== db
}