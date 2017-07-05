package alexsmirnov.pbconsole.gcode



import alexsmirnov.pbconsole.CommandSource

sealed trait Request {
  def line: String
  def source: CommandSource
}

object Request {
  val GCmd = """^[Gg](\d+).*""".r
  val MCmd = """^[Mm](\d+).*""".r
  val GCode = """^([GgMm])(\d+)\s*(.*)""".r
  def apply(line: String, source: CommandSource): Request = line.trim() match {
    case "" => GCommand(line,source) // empty line cases 'ok' response
    case GCode(cmd,n,params) => GCommand(line,source)
    case other => PlainTextRequest(line,source)
  }
}
case class PlainTextRequest(line: String,source: CommandSource) extends Request

sealed trait CommandRequest extends Request {
  def onResponse(r: Response): Unit
  def onCommandResponse(r: CommandResponse): Unit
}

case class GCommand(line: String,source: CommandSource) extends CommandRequest {
  def onResponse(r: Response) {}
  def onCommandResponse(r: CommandResponse) {}
}

case class QueryCommand(line: String,source: CommandSource,
    listener: CommandResponse => Unit,
    responseListener: Response => Unit = {_ =>()}) extends CommandRequest {
  def onResponse(r: Response) {responseListener(r)}
  def onCommandResponse(r: CommandResponse) = listener(r)
}
