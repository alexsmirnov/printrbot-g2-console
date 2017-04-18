package alexsmirnov.pbconsole

sealed trait Source {
  
}
object Source {
  case object Console extends Source
  case object Monitor extends Source
  case object Job extends Source
  case object Unknown extends Source
}