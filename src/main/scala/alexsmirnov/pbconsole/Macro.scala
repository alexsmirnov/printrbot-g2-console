package alexsmirnov.pbconsole

import java.util.HashMap
import org.apache.commons.lang3.text.StrSubstitutor
import scala.io.Source

case class Macro(name: String,description: String,content: String) {
  
}

object Macro {
  import Settings._
  def prepare(content: String,conf: Settings): Iterator[String] = {
    val values = new HashMap[String,Any]
    values.put(BED_W, conf.bedWidth())
    values.put(BED_D, conf.bedDepth())
    values.put(H, conf.height())
    val sub = new StrSubstitutor(values)
    val src = Source.fromString(sub.replace(content))
    src.getLines()
  }
}