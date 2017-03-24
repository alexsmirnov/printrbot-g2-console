package alexsmirnov.pbconsole

object SmoothieResponse {
 val OK_RESPONSE = """^ok\s?(.*)$""".r
 val ERROR_RESPONSE = """^[eE]rror\s?(.*)$""".r
 case class EmptyCommandResponse(rawLine: String,isError: Boolean = false) extends CommandResponse
 case class CommandResponseWithStatus(rawLine: String, values: List[ResponseValue], isError: Boolean = false)
 def apply(line: String): Response = line match {
   case OK_RESPONSE(msg) =>
     EmptyCommandResponse(line)
   case ERROR_RESPONSE(msg) =>
     EmptyCommandResponse(line,true)
   case other => UnknownResponse(other)
 }
}