package alexsmirnov.pbconsole

import spray.json._
import DefaultJsonProtocol._ // if you don't supply your own Protocol (see below)
import scala.util.Try

object G2Response {

  case class G2CommandResponse(r: JsValue, f: List[Int], rawLine: String) extends CommandResponse with StatusResponse {
    def status = f(1)
    def linesAvailable = f(2)
    def isError = status != 0
    def values = Nil
  }
  implicit val g2CRFormat = jsonFormat3(G2CommandResponse)

  case class G2StatusReport(sr: Map[String, Double], rawLine: String) extends StatusResponse {
    def get(field: String): Option[Double] = sr.get(field)
    def values = Nil
  }
  implicit val g2SFormat = jsonFormat2(G2StatusReport)

  case class ExceptionInfo(fb: BigDecimal, st: Int, msg: String)
  implicit val eiFormat = jsonFormat3(ExceptionInfo)
  case class ExceptionReport(er: ExceptionInfo, rawLine: String) extends Response
  implicit val erFormat = jsonFormat2(ExceptionReport)

  def apply(line: String): Response = {
    val jsonOpt = Try(JsonParser(line)).toOption
    jsonOpt.flatMap { json =>
      val jo = json.asJsObject
      extractResponse(jo.copy( jo.fields + ("rawLine"->JsString(line)) ))
    }.getOrElse(UnknownResponse(line))
  }

  def extractResponse(json: JsValue): Option[Response] = {
    val cr = safeReader[G2CommandResponse].read(json)
    val sr = cr.left.flatMap{ _ => safeReader[G2StatusReport].read(json)}
    val er = sr.left.flatMap{ _ => safeReader[ExceptionReport].read(json)}
    er.right.toOption
  }


}