package alexsmirnov.pbconsole

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Inspectors

class SmoothieResponseTest extends FlatSpec with Matchers with Inspectors {
  "extruder temp response" should "generate status with temp" in {
    val r = SmoothieResponse("ok T:180.0 /210.0 @128")
    r shouldBe a[StatusResponse]
    val values = r.asInstanceOf[StatusResponse].values
    values should contain(ExtruderTemp(180.0f))
  }
  "extruder and bed temp response" should "generate status with temp for both" in {
    val r = SmoothieResponse("ok T:180.0 /210.0 @128 B:50.0 /30.0 @0")
    r shouldBe a[StatusResponse]
    val values = r.asInstanceOf[StatusResponse].values
    values should contain allOf(ExtruderTemp(180.0f),ExtruderTarget(210.0f),ExtruderOutput(128),BedTemp(50.0f),BedTarget(30.0f),BedOutput(0))
  }
}