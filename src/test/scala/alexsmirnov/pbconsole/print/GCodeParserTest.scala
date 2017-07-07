package alexsmirnov.pbconsole.print

import org.scalatest.FlatSpec
import scala.io.Source
import org.scalatest.Matchers
import alexsmirnov.pbconsole.gcode.GCode

class GCodeParserTest extends FlatSpec with Matchers {
  
  def parseTo(cmd: String,expected: GCode) {
    it should s"parsed as $expected" in { 
       assert( GCode.apply(cmd) === expected)
    }
  }
  
  "params pattern" should "find all parameter/value pairs" in {
    println(GCode.MoveParams.findAllMatchIn("X12Y32 Z11.5 E15 F2000").map{m => m.subgroups.mkString(",")}.mkString("[","],[","]"))
  }
  "G0 with all parameters separated by spaces" should behave like parseTo("G0 X1 Y2 Z3 E100 F1500",GCode.G0Move(Some(1),Some(2),Some(3),Some(100),Some(1500),"G0 X1 Y2 Z3 E100 F1500"))
  "G0 with all parameters without spaces" should behave like parseTo("G0X1Y2Z3E100F1500",GCode.G0Move(Some(1),Some(2),Some(3),Some(100),Some(1500),"G0X1Y2Z3E100F1500"))
  "G0 with missed X parameter" should behave like parseTo("G0 Y2 Z3 E100 F1500",GCode.G0Move(None,Some(2),Some(3),Some(100),Some(1500),"G0 Y2 Z3 E100 F1500"))
  "G0 with missed E parameter" should behave like parseTo("G0 X1 Y2 Z3 F1500",GCode.G0Move(Some(1),Some(2),Some(3),None,Some(1500),"G0 X1 Y2 Z3 F1500"))
  "G0 with missed F parameter" should behave like parseTo("G0 X1 Y2 Z3 E100",GCode.G0Move(Some(1),Some(2),Some(3),Some(100),None,"G0 X1 Y2 Z3 E100"))
  "G1 with all parameters separated by spaces" should behave like parseTo("G1 X1 Y2 Z3 E100 F1500",GCode.G1Move(Some(1),Some(2),Some(3),Some(100),Some(1500),"G1 X1 Y2 Z3 E100 F1500"))
  "G1 with all parameters without spaces" should behave like parseTo("G1X1Y2Z3E100F1500",GCode.G1Move(Some(1),Some(2),Some(3),Some(100),Some(1500),"G1X1Y2Z3E100F1500"))
  "G28 line" should behave like parseTo("G28 X0",GCode.GCommand(28,"X0","G28 X0"))
  "M106 line" should behave like parseTo("M106 S200",GCode.MCommand(106,"S200","M106 S200"))
  "M106 line with leading space" should behave like parseTo("  M106 S200",GCode.MCommand(106,"S200","M106 S200"))
  "M106 line with comment" should behave like parseTo("  M106 S200 ; comment",GCode.MCommand(106,"S200","M106 S200"))
  "Empty line" should behave like parseTo("",GCode.EmptyCommand)
  "Blank line" should behave like parseTo("   ",GCode.EmptyCommand)
  "Comment line" should behave like parseTo("; comment",GCode.EmptyCommand)
  "estimate print" should "calculate boundaries and print time" in {
    val lines = Source.fromFile("3mmBox_export.gcode").getLines()
    val stats = GCode.estimatePrint(lines)
    stats.x.min should be(83.38f +- 0.01f)
    stats.x.max should be(116.62f +- 0.01f)
    stats.y.min should be(83.38f +- 0.01f)
    stats.y.max should be(116.62f +- 0.01f)
    stats.z.min should be(0.0f +- 0.5f )
    stats.z.max should be (3f +- 0.06f)
    stats.printTimeMinutes should be(3.5f +- 0.1f)
    stats.extrude should be(373.11f +- 1f)
  }
}