package alexsmirnov.pbconsole.gcode

object Response {
}

trait Response {
  def rawLine: String
}

trait CommandResponse extends Response {
  def isError: Boolean
}

trait StatusResponse extends Response {
  def values: List[ResponseValue]
}

sealed trait ResponseValue

// Temperature status
case class ExtruderTemp(value: Float,tool: Int =0) extends ResponseValue
case class ExtruderTarget(value: Float,tool: Int =0) extends ResponseValue
case class ExtruderOutput(pwm: Int,tool: Int =0) extends ResponseValue
case class BedTemp(value: Float) extends ResponseValue
case class BedTarget(value: Float) extends ResponseValue
case class BedOutput(pwm: Int) extends ResponseValue
// Current position
case class PositionX(value: Float) extends ResponseValue
case class PositionY(value: Float) extends ResponseValue
case class PositionZ(value: Float) extends ResponseValue
case class PositionE(value: Float) extends ResponseValue

case class EmptyCommandResponse(rawLine: String, isError: Boolean = false) extends CommandResponse
case class HaltedResponse(rawLine: String) extends CommandResponse {
    def isError: Boolean = true
}
case class CommandResponseWithStatus(rawLine: String, values: List[ResponseValue], isError: Boolean = false) extends StatusResponse with CommandResponse
case class StatusOnlyResponse(rawLine: String, values: List[ResponseValue]) extends StatusResponse
case class UnknownResponse(rawLine: String) extends Response
case class ErrorResponse(rawLine: String,msg: String) extends Response
