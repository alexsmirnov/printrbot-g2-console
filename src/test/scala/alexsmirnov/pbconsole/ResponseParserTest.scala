package alexsmirnov.pbconsole

import org.scalatest.WordSpec
import org.scalatest.Matchers
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

class ResponseParserTest extends WordSpec with Matchers {
  "parse" when { 
    "parse malformed JSON" should {
      "return UnknownResponse" in {
        Response("{foo:n/x") should be (UnknownResponse("{foo:n/x"))
      }
    }
    "parse response" should {
      "return CommandResponse" in {
        Response("""{"r":{"xjm":5000000000.000},"f":[3,0,6]}""") should be(
            CommandResponse(("xjm"-> 5000000000.000),List(3,0,6),"""{"r":{"xjm":5000000000.000},"f":[3,0,6]}"""))
      }
    }
    "parse status report" should {
      "return StatusReport" in {
        val r = Response("""{"sr":{"line":0,"posx":0.000,"posy":0.000,"posz":0.000,"posa":0.000,"vel":0.000,"momo":1,"stat":3}}""")
        r should be(StatusReport(Map("line"->0,"posx"->0.000,"posy"->0.000,"posz"->0.000,"posa"->0.000,"vel"->0.000,"momo"->1,"stat"->3),
            """{"sr":{"line":0,"posx":0.000,"posy":0.000,"posz":0.000,"posa":0.000,"vel":0.000,"momo":1,"stat":3}}"""))
      }
    }
  }
}