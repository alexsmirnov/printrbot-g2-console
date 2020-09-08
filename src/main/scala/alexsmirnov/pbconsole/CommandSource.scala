package alexsmirnov.pbconsole

sealed trait CommandSource {
  def abbr: String
}
object CommandSource {
  case object Console extends CommandSource{
    val abbr = "C"
  }
  case object Monitor extends CommandSource{
    val abbr = "M"
  }
  case object Job extends CommandSource{
    val abbr = "J"
  }
  case object Unknown extends CommandSource{
    val abbr = "?"
  }
}