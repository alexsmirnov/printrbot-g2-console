package alexsmirnov.pbconsole

import java.util.HashMap
import org.apache.commons.lang3.text.StrSubstitutor
import scala.io.Source
import scalafx.beans.property.StringProperty
import javafx.util.Callback
import javafx.beans.Observable

class Macro {
  val nameProperty = StringProperty("")
  def name = nameProperty.get
  def name_=(v: String) = nameProperty.update(v)
  val descriptionProperty = StringProperty("")
  def description = descriptionProperty.get
  def description_=(v: String) = descriptionProperty.update(v)
  val contentProperty = StringProperty("")
  def content = contentProperty.get
  def content_=(v: String) = contentProperty.update(v)
}

object Macro {
  import Settings._
  object extractor extends Callback[Macro,Array[Observable]]{
    def call(m: Macro) = Array(m.nameProperty,m.descriptionProperty,m.contentProperty)
  }
  def apply(name: String,description: String,content: String) = {
    val m = new Macro
    m.name = name
    m.description = description
    m.content = content
    m
  }
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