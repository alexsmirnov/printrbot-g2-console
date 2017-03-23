package alexsmirnov.pbconsole

object Response {
}

trait Response {
  def rawLine: String
}

trait HiddenResponse extends Response

trait CommandResponse extends Response {
  def isError: Boolean
}

trait StatusResponse extends Response {
  def values: List[ResponseValue]
}

trait ResponseValue

// Temperature status
case class ExtruderTemp(value: Float) extends ResponseValue
case class ExtruderTarget(value: Float) extends ResponseValue
case class ExtruderOutput(pwm: Int) extends ResponseValue
case class BedTemp(value: Float) extends ResponseValue
case class BedTarget(value: Float) extends ResponseValue
case class BedOutput(pwm: Int) extends ResponseValue
// Current position
case class PositionX(value: Float) extends ResponseValue
case class PositionY(value: Float) extends ResponseValue
case class PositionZ(value: Float) extends ResponseValue
case class PositionE(value: Float) extends ResponseValue

case class UnknownResponse(rawLine: String) extends Response
