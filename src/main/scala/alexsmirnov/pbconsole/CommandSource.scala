package alexsmirnov.pbconsole

sealed trait CommandSource {
  
}
object CommandSource {
  case object Console extends CommandSource
  case object Monitor extends CommandSource
  case object Job extends CommandSource
  case object Unknown extends CommandSource
}