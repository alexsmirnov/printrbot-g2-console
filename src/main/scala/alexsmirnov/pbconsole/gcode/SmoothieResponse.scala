package alexsmirnov.pbconsole.gcode

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
  val extruderTempResponse = """.*T:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+).*""".r
  val bedTempResponse = """.*B:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+).*""".r
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
  def values(msg: String) = valueMatchers.flatMap { pf => pf.applyOrElse(msg, { _: String => Nil }) }
  
  def apply(line: String): Response = line match {
    case "ok"| "Ok" =>
        EmptyCommandResponse(line)
    case OK_RESPONSE(msg) =>
      val responses = values(msg)
        CommandResponseWithStatus(line, responses)
    case ERROR_RESPONSE(msg) =>
      ErrorResponse(line, msg)
    case HALTED_RESPONSE() =>
      HaltedResponse(line)
    case other => 
      val responses = values(other)
      if (responses.isEmpty) {
        UnknownResponse(other)
      } else {
        StatusOnlyResponse(line, responses)
      }
  }
}