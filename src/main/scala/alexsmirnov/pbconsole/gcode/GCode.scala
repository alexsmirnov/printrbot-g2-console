package alexsmirnov.pbconsole.gcode

sealed trait GCode {
  def line: String
}

object GCode {

  val G0Cmd = """^\s*G0(\D.*)""".r
  val G1Cmd = """^\s*G1(\D.*)""".r
  val G92Cmd = """^\s*G92(\D.*)""".r
  val M104Cmd = """^\s*M104\s*S(\d+\.?\d*).*""".r
  val M109Cmd = """^\s*M109\s*S(\d+\.?\d*).*""".r
  val M140Cmd = """^\s*M140\s*S(\d+\.?\d*).*""".r
  val M190Cmd = """^\s*M190\s*S(\d+\.?\d*).*""".r
  val GCmd = """^G(\d+)\s*(.*)""".r
  val MCmd = """^M(\d+)\s*(.*)""".r
  val MoveParams = """\s*([XYZABEF])(\d+\.?\d*)""".r
  val NoComment = """^\s*([^;]+)\s*;?.*$""".r

  def dist(from: Option[Float], to: Option[Float]): Float = from.flatMap { f => to.map(_ - f) }.getOrElse(0.0f)

  case class Position(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Float) {
    def moveTo(move: Move) = new Position(
      move.x.orElse(x),
      move.y.orElse(y),
      move.z.orElse(z),
      move.extruder.orElse(extruder),
      move.speed.getOrElse(speed))
    def distance(next: Position) = {
      val dx = dist(x, next.x)
      val dy = dist(y, next.y)
      val dz = dist(z, next.z)
      math.sqrt(dx * dx + dy * dy + dz * dz).toFloat
    }
    def travelTime(next: Position) = {
      val d = distance(next)
      if (d > 0) d / next.speed else dist(extruder, next.extruder) / next.speed
    }
  }

  val UnknownPosition = Position(None, None, None, None, 1.0f)
  /**
   * Command expects response from printer firmware
   */
  trait Command extends GCode

  sealed trait Move extends Command {
    def x: Option[Float]
    def y: Option[Float]
    def z: Option[Float]
    def extruder: Option[Float]
    def speed: Option[Float]
  }
  
  case class G0Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float], line: String) extends Move
  object G0Move {
    def apply(p: Map[Char, Float], command: String) = new G0Move(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')), p.get('F'), command)
  }
  case class G1Move(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], speed: Option[Float], line: String) extends Move
  object G1Move {
    def apply(p: Map[Char, Float], command: String) = new G1Move(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')), p.get('F'), command)
  }
  case class G92SetPos(x: Option[Float], y: Option[Float], z: Option[Float], extruder: Option[Float], line: String) extends Move {
    val speed = None
  }
  object G92SetPos {
    def apply(p: Map[Char, Float], command: String) = new G92SetPos(p.get('X'), p.get('Y'), p.get('Z'), p.get('E').orElse(p.get('A')), command)
  }
  case class BedTempCommand(t: Float) extends Command {
    val line = s"M140 S$t"
  }
  case class BedTempAndWaitCommand(t: Float) extends Command {
    val line = s"M190 S$t"
  }
  case class ExtTempCommand(t: Float) extends Command {
    val line = s"M104 S$t"
  }
  case class ExtTempAndWaitCommand(t: Float) extends Command {
    val line = s"M109 S$t"
  }
  
  case class GCommand(n: Int, params: String, line: String) extends Command
  
  case class MCommand(n: Int, params: String, line: String) extends Command
  
  case class UnknownCommand(line: String) extends GCode

  case object EmptyCommand extends Command {
    val line = ""
  }

  def stripComment(line: String) = NoComment.findFirstMatchIn(line).map(_.subgroups(0)).getOrElse("")

  def parseParams(params: String) = MoveParams.findAllMatchIn(params).map { m => m.subgroups(0).head -> (m.subgroups(1).toFloat) }.toMap

  def parse(line: String): GCode = stripComment(line) match {
    case "" => EmptyCommand
    case G0Cmd(params) => G0Move(parseParams(params), line)
    case G1Cmd(params) => G1Move(parseParams(params), line)
    case G92Cmd(params) => G92SetPos(parseParams(params), line)
    case M104Cmd(temp) => ExtTempCommand(temp.toFloat)
    case M109Cmd(temp) => ExtTempAndWaitCommand(temp.toFloat)
    case M140Cmd(temp) => BedTempCommand(temp.toFloat)
    case M190Cmd(temp) => BedTempAndWaitCommand(temp.toFloat)
    case GCmd(n,params) => GCommand(n.toInt,params,line)
    case MCmd(n,params) => MCommand(n.toInt,params,line)
    case other => UnknownCommand(other)
  }

  case class range(min: Float = Float.MaxValue, max: Float = Float.MinValue) {
    def update(value: Option[Float]) = value map { v =>
      val newMin = min.min(v)
      val newMax = max.max(v)
      range(newMin, newMax)
    } getOrElse (this)
    def size = max - min
  }

  case class PrintStats(x: range,
    y: range,
    z: range,
    extrude: Float,
    printTimeMinutes: Float)

  val EmptyStats = PrintStats(range(), range(), range(), 0f, 0f)
  val ZeroStats = PrintStats(range(0, 0), range(0, 0), range(0, 0), 0f, 0L)
  def estimatePrint(lines: Iterator[String]) = processProgram(lines) { (_, _, _) => () }

  def processProgram(lines: Iterator[String])(callback: (GCode, Position, PrintStats) => Unit): PrintStats = {
    lines.foldLeft((EmptyStats, UnknownPosition)) { (stp, line) =>
      val (currentStats, currentPosition) = stp
      parse(line) match {
        case EmptyCommand => stp
        case move: G92SetPos =>
          val nextPosition = currentPosition.moveTo(move)
          callback(move, nextPosition, currentStats)
          (currentStats, nextPosition)
        case move: Move =>
          val nextPosition = currentPosition.moveTo(move)
          val PrintStats(rangeX, rangeY, rangeZ, ex, time) = currentStats
          val nextStats = PrintStats(rangeX.update(nextPosition.x),
            rangeY.update(nextPosition.y),
            rangeZ.update(nextPosition.z),
            ex + dist(currentPosition.extruder, nextPosition.extruder),
            time + currentPosition.travelTime(nextPosition))
          callback(move, nextPosition, nextStats)
          (nextStats, nextPosition)
        case other =>
          callback(other, currentPosition, currentStats)
          stp
      }
    }._1
  }
}