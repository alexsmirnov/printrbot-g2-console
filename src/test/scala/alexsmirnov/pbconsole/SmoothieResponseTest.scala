package alexsmirnov.pbconsole

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Inspectors
import alexsmirnov.pbconsole.gcode.SmoothieResponse
import alexsmirnov.pbconsole.gcode.StatusResponse
import alexsmirnov.pbconsole.gcode.ExtruderTemp
import alexsmirnov.pbconsole.gcode.ExtruderTarget
import alexsmirnov.pbconsole.gcode.ExtruderOutput
import alexsmirnov.pbconsole.gcode.BedTemp
import alexsmirnov.pbconsole.gcode.BedTarget
import alexsmirnov.pbconsole.gcode.BedOutput
import alexsmirnov.pbconsole.gcode.ErrorResponse

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
  "2 extruders and bed temp response" should "generate status with temp for both" in {
    val r = SmoothieResponse("ok T:180.0 /210.0 @128 T1:190.0 /200.0 @255 B:50.0 /30.0 @0")
    r shouldBe a[StatusResponse]
    val values = r.asInstanceOf[StatusResponse].values
    values should contain allOf(ExtruderTemp(180.0f),
                                ExtruderTarget(210.0f),
                                ExtruderOutput(128),
                                ExtruderTemp(190.0f,1),
                                ExtruderTarget(200.0f,1),
                                ExtruderOutput(255,1),
                       BedTemp(50.0f),BedTarget(30.0f),BedOutput(0))
  }
  "Marlin response" should "contain both extruder and bed temperature" in {
    val r = SmoothieResponse("ok T:180.0 /210.0 T0:180.0 /210.0 T1:190.0 /200.0 B:50.0 /30.0 @:128 @0:128 @1:255 @B:127")
    r shouldBe a[StatusResponse]
    val values = r.asInstanceOf[StatusResponse].values
    values should contain allOf(ExtruderTemp(180.0f),
      ExtruderTarget(210.0f),
      ExtruderTemp(190.0f,1),
      ExtruderTarget(200.0f,1),
      BedTemp(50.0f),BedTarget(30.0f))

  }
  "Marlin response" should "contain both extruder and bed output" in {
    val r = SmoothieResponse("ok T:180.0 /210.0 T0:180.0 /210.0 T1:190.0 /200.0 B:50.0 /30.0 @:128 @0:128 @1:255 @B:127")
    r shouldBe a[StatusResponse]
    val values = r.asInstanceOf[StatusResponse].values
    values should contain allOf(ExtruderOutput(128),
      ExtruderOutput(255,1),
      BedOutput(127))

  }
  "error: response" should "generate error" in {

    val r = SmoothieResponse("error:Only G38.2 suppoerted")
    r shouldBe a[ErrorResponse]
  }
  "error response" should "generate error" in {
    
    val r = SmoothieResponse("error Only G38.2 suppoerted")
    r shouldBe a[ErrorResponse]
  }
}