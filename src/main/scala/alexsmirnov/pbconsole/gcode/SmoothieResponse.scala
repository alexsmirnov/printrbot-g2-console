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
  val extruderTempResponse = """T:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+)""".r.unanchored
  def extruderNTempResponse(tool: Int) = (s"T${tool}"+""":\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+)""").r.unanchored
  val bedTempResponse = """B:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s+@(\d+)""".r.unanchored
  val positionResponse = """C:\s*X(\d+\.?\d*)\s*Y(\d+\.?\d*)\s*Z(\d+\.?\d*)""".r.unanchored
  val extruderResponse = """E(\d+\.?\d*)""".r.unanchored
  // Marlin responses
  val marlinExtruderTempResponse = """T:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)""".r.unanchored
  def marlinExtruderNTempResponse(tool: Int) = ("T"+tool+""":\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s""").r.unanchored
  val marlinBedTempResponse = """B:\s*(\d+\.?\d*)\s+/(\d+\.?\d*)\s""".r.unanchored
  def marlinToolOutputMatcher(tool: Int) = ("@"+tool+""":(\d+)""").r.unanchored
  val marlinBedOutputMatcher = """B@:(\d+)""".r.unanchored

  def temperatureMatcher(tool: Int):PartialFunction[String, Seq[ResponseValue]] = {
     val tempPattern = extruderNTempResponse(tool)
     val marlinTempPattern = marlinExtruderNTempResponse(tool)
    return {
      case tempPattern(float(current), float(target), int(output)) => List(
        ExtruderTemp(current,tool),
        ExtruderTarget(target,tool),
        ExtruderOutput(output,tool))
      case marlinTempPattern(float(current), float(target)) => List(
        ExtruderTemp(current,tool),
        ExtruderTarget(target,tool) )
    }
  }
  def outputMatcher(tool: Int):PartialFunction[String, Seq[ResponseValue]] = {
    val regex = marlinToolOutputMatcher(tool)
    return {
      case regex(int(output)) => List( ExtruderOutput(output,tool) )
    }
  }

  val valueMatchers: List[PartialFunction[String, Seq[ResponseValue]]] = List(
    {
      case extruderTempResponse(float(current), float(target), int(output)) => List(
        ExtruderTemp(current),
        ExtruderTarget(target),
        ExtruderOutput(output))
      case marlinExtruderTempResponse(float(current), float(target), int(output)) => List(
        ExtruderTemp(current),
        ExtruderTarget(target))
    },
    {
      case bedTempResponse(float(current), float(target), int(output)) => List(
        BedTemp(current.toFloat),
        BedTarget(target.toFloat),
        BedOutput(output.toInt))
      case marlinBedTempResponse(float(current), float(target)) => List(
        BedTemp(current.toFloat),
        BedTarget(target.toFloat))
    },
    {
      case marlinBedOutputMatcher(int(output)) => List(BedOutput(output))
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

  val allMatchers = valueMatchers ++ (0 to 1).map(temperatureMatcher) ++ (0 to 1).map(outputMatcher)

  def values(msg: String) = allMatchers.flatMap { pf => pf.applyOrElse(msg, { _: String => Nil }) }
  
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