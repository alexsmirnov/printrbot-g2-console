package alexsmirnov.pbconsole
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.prefs.EmptyValueStrategy

object Response {
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
      json.extractOpt[CommandResponse]
    } else if (json \ "sr" != JNothing) {
      json.extractOpt[StatusReport]
    } else if (json \ "er" != JNothing) {
      json.extractOpt[ExceptionReport]
    } else None
  }
}

trait Response {
  def rawLine: String
}

case class UnknownResponse(rawLine: String) extends Response

case class CommandResponse(r: JValue, f: List[Int], rawLine: String) extends Response {
  def status = f(1)
  def linesAvailable = f(2)
}

case class StatusReport(sr: Map[String, Double], rawLine: String) extends Response {
  def get(field: String): Option[Double] = sr.get(field)
}
case class ExceptionInfo(fb: BigDecimal, st: Int, msg: String)
case class ExceptionReport(er: ExceptionInfo, rawLine: String) extends Response