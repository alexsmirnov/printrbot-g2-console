package alexsmirnov.pbconsole

import scala.util.Try

object SmoothieResponse {
  case object float {
    def unapply(s: String): Option[Float] = Try(s.toFloat).toOption
  }
  case object int {
    def unapply(s: String): Option[Int] = Try(s.toInt).toOption
  }
  val OK_RESPONSE = """^ok\s+(.*)$""".r
  val HALTED_RESPONSE = """^!!""".r
  val ERROR_RESPONSE = """^[eE]rror\s?(.*)$""".r
  val extruderTempResponse = """.*T:(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+).*""".r
  val bedTempResponse = """.*B:(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+).*""".r
  val positionResponse = """C:\s*X(\d+\.?\d*)\s*Y(\d+\.?\d*)\s*Z(\d+\.?\d*).*""".r
  val extruderResponse = """.*E(\d+\.?\d*)""".r
  val valueMatchers: List[PartialFunction[String, Seq[ResponseValue]]] = List(
    {
      case extruderTempResponse(float(current), float(target), int(output)) => List(
        ExtruderTemp(current),
        ExtruderTarget(target),
        ExtruderOutput(output))
    },
    {
      case bedTempResponse(float(current), float(target), int(output)) => List(
        BedTemp(current.toFloat),
        BedTarget(target.toFloat),
        BedOutput(output.toInt))
    },
    {
      case positionResponse(float(x), float(y), float(z)) => List(
        PositionX(x),
        PositionY(y),
        PositionZ(z))
    },
    {
      case extruderResponse(float(e)) => List(
        PositionE(e))
    })
  case class EmptyCommandResponse(rawLine: String, isError: Boolean = false) extends CommandResponse
  case class HaltedResponse(rawLine: String) extends CommandResponse {
    def isError: Boolean = true
  }
  case class CommandResponseWithStatus(rawLine: String, values: List[ResponseValue], isError: Boolean = false) extends StatusResponse with CommandResponse
  
  def apply(line: String): Response = line match {
    case OK_RESPONSE(msg) =>
      val responses = valueMatchers.flatMap { pf => pf.applyOrElse(msg, { _: String => Nil }) }
      if (responses.isEmpty) {
        EmptyCommandResponse(line)
      } else {
        CommandResponseWithStatus(line, responses)
      }
    case ERROR_RESPONSE(msg) =>
      EmptyCommandResponse(line, true)
    case HALTED_RESPONSE() =>
      HaltedResponse(line)
    case other => UnknownResponse(other)
  }
}