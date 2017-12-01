package alexsmirnov.scalafx

import scala.language.implicitConversions
import scala.collection.JavaConverters._
import javafx.beans.binding.DoubleBinding
import javafx.beans.binding.FloatBinding
import javafx.beans.binding.IntegerBinding
import scalafx.application.Platform
import scalafx.beans.Observable
import scalafx.beans.binding.BindingIncludes._
import scalafx.beans.binding.BooleanBinding
import scalafx.beans.binding.ObjectBinding
import scalafx.beans.binding.StringBinding
import scalafx.beans.binding.NumberBinding
import scalafx.beans.property.Property
import scalafx.beans.value.ObservableValue
import scalafx.collections.ObservableBuffer
import javafx.collections.ObservableList
import java.util.function.Predicate

sealed trait BindingBuilder[A, B] {
  def createBinding(f: () => A, deps: Observable*): B
}

trait LowPriorityObservableImplicits {
  implicit def ObjectBind[T <: AnyRef]: BindingBuilder[T, ObjectBinding[T]] = new BindingBuilder[T, ObjectBinding[T]] {
    def createBinding(f: () => T, deps: Observable*) = createObjectBinding(f, deps: _*)
  }
}

trait ObservableImplicits extends LowPriorityObservableImplicits {

  implicit object BooleanBind extends BindingBuilder[Boolean, BooleanBinding] {
    def createBinding(f: () => Boolean, deps: Observable*) = createBooleanBinding(f, deps: _*)
  }

  implicit object StringBind extends BindingBuilder[String, StringBinding] {
    def createBinding(f: () => String, deps: Observable*) = createStringBinding(f, deps: _*)
  }

  implicit object IntegerBind extends BindingBuilder[Int, IntegerBinding] {
    def createBinding(f: () => Int, deps: Observable*) = createIntegerBinding(f, deps: _*)
  }

  implicit object FloatBind extends BindingBuilder[Float, FloatBinding] {
    def createBinding(f: () => Float, deps: Observable*) = createFloatBinding(f, deps: _*)
  }

  implicit object DoubleBind extends BindingBuilder[Double, DoubleBinding] {
    def createBinding(f: () => Double, deps: Observable*) = createDoubleBinding(f, deps: _*)
  }

  implicit class OvOps[A](ov: ObservableValue[A, _]) {
    def map[B, Bind](f: A => B)(implicit bb: BindingBuilder[B, Bind]): Bind = {
      bb.createBinding({ () => f(ov.value) }, ov)
    }
    def zip[B](other: ObservableValue[B, _]) = createObjectBinding({ () => (ov.value, other.value) }, ov, other)
  }

  implicit class PropertyOps[A, J](prop: Property[A, J]) {
    def asListener[T](f: T => A): (T => Unit) = { t: T =>
      runInFxThread(prop.update(f(t)))
    }
  }
  def runInFxThread[A](f: => A) {
    if (Platform.isFxApplicationThread) {
      f
    } else {
      Platform.runLater(f)
    }
  }
  
  implicit def predicate[A](f: A => Boolean): Predicate[A] = new Predicate[A]{
    def test(a: A) = f(a)
  }

  implicit class ObservableBufferOps[A](val buff: ObservableBuffer[A]) {
    def bindMap[B](other: ObservableList[B])(f: A => B) = {
      other.clear()
      buff.foreach { a => other.add(f(a)) }
      buff.onChange((_, changes) => {
        for (change <- changes)
          change match {
            case ObservableBuffer.Add(pos, added) =>
              val addedPanes = added.toSeq.map { m => f(m) }
              other.addAll(pos, addedPanes.asJavaCollection)
            case ObservableBuffer.Remove(pos, removed) => other.remove(pos, pos + removed.size)
            case ObservableBuffer.Reorder(from, to, permutation) =>
              val changed = List(other.subList(from, to).asScala: _*)
              changed.zipWithIndex.foreach { case (b, pos) => other.set(permutation(pos + from), b) }
            case ObservableBuffer.Update(pos, updated) => 
//              val changed = buff.slice(pos, pos+updated).map(f)
//              other.setAll(changed.asJavaCollection)
          }
      })
    }
  }
}
object ObservableImplicits extends ObservableImplicits