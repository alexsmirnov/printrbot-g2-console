package alexsmirnov.pbconsole

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.prefs.EmptyValueStrategy
object G2Response {

  implicit val formats = new DefaultFormats {
    // Brings in default date formats etc.
    override val allowNull: Boolean = true
    override val wantsBigDecimal: Boolean = true
    override val emptyValueStrategy: EmptyValueStrategy = new EmptyValueStrategy {
      def noneValReplacement = None

      def replaceEmpty(value: JValue) = value match {
        case JNothing => throw new MappingException("Missed value")
        case oth => oth
      }
    }
  }

  def apply(line: String): Response = {
    val jsonOpt = parseOpt(line)
    jsonOpt.flatMap { json =>
      val rawLineJson: JValue = ("rawLine" -> line)
      extractResponse(json merge rawLineJson)
    }.getOrElse(UnknownResponse(line))
  }

  def extractResponse(json: JValue): Option[Response] = {
    if (json \ "r" != JNothing) {
      json.extractOpt[G2CommandResponse]
    } else if (json \ "sr" != JNothing) {
      json.extractOpt[G2StatusReport]
    } else if (json \ "er" != JNothing) {
      json.extractOpt[ExceptionReport]
    } else None
  }

  case class G2CommandResponse(r: JValue, f: List[Int], rawLine: String) extends CommandResponse with StatusResponse {
    def status = f(1)
    def linesAvailable = f(2)
    def isError = status != 0
    def values = Nil
  }

  case class G2StatusReport(sr: Map[String, Double], rawLine: String) extends StatusResponse {
    def get(field: String): Option[Double] = sr.get(field)
    def values = Nil
  }

  case class ExceptionInfo(fb: BigDecimal, st: Int, msg: String)
  case class ExceptionReport(er: ExceptionInfo, rawLine: String) extends Response

}