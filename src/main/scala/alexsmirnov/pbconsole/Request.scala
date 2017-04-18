package alexsmirnov.pbconsole

sealed trait Request {
  def line: String
  def source: Source
}

case class PlainTextRequest(line: String,source: Source) extends Request

sealed trait CommandRequest extends Request {
  def onResponse(r: Response): Unit
  def onCommandResponse(r: CommandResponse): Unit
}

case class GCommand(line: String,source: Source) extends CommandRequest {
  def onResponse(r: Response) {}
  def onCommandResponse(r: CommandResponse) {}
}

case class QueryCommand(line: String,source: Source, listener: CommandResponse => Unit) extends CommandRequest {
  def onResponse(r: Response) {}
  def onCommandResponse(r: CommandResponse) = listener(r)
}
