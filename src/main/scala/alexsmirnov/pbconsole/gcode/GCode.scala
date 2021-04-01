package alexsmirnov.pbconsole.gcode

sealed trait GCode {
  def line: String
}

object GCode {

  val G0Cmd = """^\s*G0(\D.*)""".r
  val G1Cmd = """^\s*G1(\D.*)""".r
  val G92Cmd = """^\s*G92(\D.*)""".r
  val M104Cmd = """^\s*M104\s*S(\d+\.?\d*).*""".r
  val M104ToolCmd = """^\s*M104\s*S(\d+\.?\d*)\s*T(\d+).*""".r
  val M109Cmd = """^\s*M109\s*S(\d+\.?\d*).*""".r
  val M109ToolCmd = """^\s*M109\s*S(\d+\.?\d*)\s*T(\d+).*""".r
  val M140Cmd = """^\s*M140\s*S(\d+\.?\d*).*""".r
  val M190Cmd = """^\s*M190\s*S(\d+\.?\d*).*""".r
  val GCmd = """^G(\d+)\s*(.*)""".r
  val MCmd = """^M(\d+)\s*(.*)""".r
  val MoveParams = """\s*([XYZABEF])(\d+\.?\d*)""".r
  val NoComment = """^\s*([^;]+)\s*;?.*$""".r
  val Tool = """^(.*)T(\d+)(.*)$""".r
  val ToolCmd = """^\s*T(\d+).*""".r
  val Empty = """^(\s*)$""".r

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

  def toolParam(t: Option[Int]):String = {t.map({ n:Int => s" T$n"}).getOrElse("");}

  case class ExtTempCommand(t: Float,tool: Option[Int] = None) extends Command {
    val line = s"M104 S$t${toolParam(tool)}"
  }
  case class ExtTempAndWaitCommand(t: Float,tool: Option[Int] = None) extends Command {
    val line = s"M109 S$t${toolParam(tool)}"
  }

  case class GCommand(n: Int, params: String, line: String) extends Command

  case class MCommand(n: Int, params: String, line: String) extends Command
  
  case class ToolCommand(n: Int) extends Command {
    val line: String = "T"+n
  }

  case class UnknownCommand(line: String) extends GCode

  case object EmptyCommand extends Command {
    val line = ""
  }
  // Predefined commands
  val M105 = MCommand(105,"","M105")
  val M82 = MCommand(82,"","M82")
  val M83 = MCommand(83,"","M83")
  val G90 = GCommand(90,"","G90")
  val G91 = GCommand(91,"","G91")
  val G28 = GCommand(28,"","G28")

  def stripComment(line: String) = NoComment.findFirstMatchIn(line).map(_.subgroups(0).trim).getOrElse("")

  def parseParams(params: String) = MoveParams.findAllMatchIn(params).map { m => m.subgroups(0).head -> (m.subgroups(1).toFloat) }.toMap

  def apply(line: String): List[GCode] = {
    val strip = stripComment(line)
    strip match {
      // Special case from Slic3r and cura : tool parameter for extruder temp command
      case M109ToolCmd(temp,tool) => List(ExtTempAndWaitCommand(temp.toFloat,Some(tool.toInt)))
      case M104ToolCmd(temp,tool) => List(ExtTempCommand(temp.toFloat,Some(tool.toInt)))
      // separate tool command from rest of line. needed for smootieware only
      case ToolCmd(n) => List(ToolCommand(n.toInt))
      //case Tool(a,n,b) => List(ToolCommand(n.toInt), parse(a+b))
      case _ => List(parse(strip))
    }
  }
  def parse(strip: String): GCode = {  
    strip match {
      case Empty(_) => EmptyCommand
      case G0Cmd(params) => G0Move(parseParams(params), strip)
      case G1Cmd(params) => G1Move(parseParams(params), strip)
      case G92Cmd(params) => G92SetPos(parseParams(params), strip)
      case M104Cmd(temp) => ExtTempCommand(temp.toFloat)
      case M109Cmd(temp) => ExtTempAndWaitCommand(temp.toFloat)
      case M140Cmd(temp) => BedTempCommand(temp.toFloat)
      case M190Cmd(temp) => BedTempAndWaitCommand(temp.toFloat)
      case GCmd(n, params) => GCommand(n.toInt, params, strip)
      case MCmd(n, params) => MCommand(n.toInt, params, strip)
      case ToolCmd(n) => ToolCommand(n.toInt)
      case other => UnknownCommand(other)
    }
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
    printTimeMinutes: Float,
    currentPosition: Position)

  val EmptyStats = PrintStats(range(), range(), range(), 0f, 0f,UnknownPosition)
  val ZeroStats = PrintStats(range(0, 0), range(0, 0), range(0, 0), 0f, 0L,UnknownPosition)
  def estimatePrint(lines: Iterator[String]) = processProgram(lines).foldLeft(ZeroStats){ (l,r) => r._2 }
  def processProgram(lines: Iterator[String]): Iterator[(GCode,PrintStats)] = {
    lines.flatMap(apply).scanLeft[(GCode,PrintStats)]((EmptyCommand,EmptyStats)) { (stp, cmd) =>
      val (_,currentStats) = stp
      cmd match {
        case move: G92SetPos =>
          val nextPosition = currentStats.currentPosition.moveTo(move)
          (move,  currentStats.copy(currentPosition=nextPosition))
        case move: Move =>
          val PrintStats(rangeX, rangeY, rangeZ, ex, time,currentPosition) = currentStats
          val nextPosition = currentPosition.moveTo(move)
          val nextStats = PrintStats(rangeX.update(nextPosition.x),
            rangeY.update(nextPosition.y),
            rangeZ.update(nextPosition.z),
            ex + dist(currentPosition.extruder, nextPosition.extruder),
            time + currentPosition.travelTime(nextPosition),
            nextPosition)
          (move, nextStats)
        case other =>
          (other, currentStats)
      }
    }.filter(_._1 != EmptyCommand)
  }
}