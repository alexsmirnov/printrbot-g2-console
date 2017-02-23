package alexsmirnov.pbconsole

import alexsmirnov.stream.ReactiveOps._

package object serial {
  
  val NL = '\n'.toByte

  def toLines = fold[Byte, String, StringBuilder](
    StringBuilder.newBuilder,
    { (b, acc) =>
      b.toChar match {
        case '\r' => Left(acc)
        case '\n' => Right(acc.result())
        case c => Left(acc.+=(c))
      }
    },
    { sb => sb.result() })

  def linesToBytes = flatMap[String, Byte](_.getBytes.toSeq :+ NL)
}