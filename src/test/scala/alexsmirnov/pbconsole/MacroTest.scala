package alexsmirnov.pbconsole

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class MacroTest extends FlatSpec with Matchers {
  "Empty string" should "prepared as empty iterator" in {
    val m = Macro("foo","","")
    Macro.prepare("", new Settings) shouldBe empty
  }
  "Single line string" should "prepared as single value" in {
    Macro.prepare("foo", new Settings).toStream should contain only("foo")
  }
  "Multiline string" should "prepared as single value" in {
    Macro.prepare("foo command\n;bar comment\nbaz\n\nend line", new Settings).toStream should contain inOrderOnly("foo command",";bar comment","baz","","end line")
  }
  "variable substitution" should "substitute bed width and depth and height" in {
    val s = new Settings
    s.bedWidth.update(12.0)
    s.bedDepth.update(110.0)
    s.height.update(22.00)
    Macro.prepare("G1 X${bedWidth} Y${bedDepth} Z${height}", s).toStream should contain only("G1 X12.0 Y110.0 Z22.0")
  }
}