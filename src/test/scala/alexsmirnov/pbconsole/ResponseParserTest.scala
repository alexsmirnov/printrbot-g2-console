package alexsmirnov.pbconsole

import org.scalatest.WordSpec
import org.scalatest.Matchers
import spray.json._
import DefaultJsonProtocol._ // if you don't supply your own Protocol (see below)

class ResponseParserTest extends WordSpec with Matchers {
  "parse" when { 
    "parse malformed JSON" should {
      "return UnknownResponse" in {
        G2Response("{foo:n/x") should be (UnknownResponse("{foo:n/x"))
      }
    }
    "parse response" should {
      "return CommandResponse" in {
        G2Response("""{"r":{"xjm":5000000000.000},"f":[3,0,6]}""") should be(
            G2Response.G2CommandResponse(JsObject(Map("xjm"-> JsNumber(5000000000.000))),List(3,0,6),"""{"r":{"xjm":5000000000.000},"f":[3,0,6]}"""))
      }
    }
    "parse status report" should {
      "return StatusReport" in {
        val r = G2Response("""{"sr":{"line":0,"posx":0.000,"posy":0.000,"posz":0.000,"posa":0.000,"vel":0.000,"momo":1,"stat":3}}""")
        r should be(G2Response.G2StatusReport(Map("line"->0,"posx"->0.000,"posy"->0.000,"posz"->0.000,"posa"->0.000,"vel"->0.000,"momo"->1,"stat"->3),
            """{"sr":{"line":0,"posx":0.000,"posy":0.000,"posz":0.000,"posa":0.000,"vel":0.000,"momo":1,"stat":3}}"""))
      }
    }
  }
}