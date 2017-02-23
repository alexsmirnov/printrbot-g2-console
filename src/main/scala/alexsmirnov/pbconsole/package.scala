package alexsmirnov
import scalafx.beans.value.ObservableValue
import scalafx.beans.Observable
import javafx.beans.binding.{IntegerBinding,FloatBinding,DoubleBinding}
import scalafx.beans.binding.{BooleanBinding,StringBinding,ObjectBinding,NumberBinding}
import scalafx.beans.binding.BindingIncludes._
import scalafx.beans.property.Property
import scalafx.application.Platform

package object pbconsole {
  sealed trait BindingBuilder[A,B]{
    def createBinding(f: () => A,deps: Observable*): B
  }
  
  implicit object BooleanBind extends BindingBuilder[Boolean,BooleanBinding]{
    def createBinding(f: () => Boolean,deps: Observable*) = createBooleanBinding(f, deps: _*)
  }
 
  implicit object StringBind extends BindingBuilder[String,StringBinding]{
    def createBinding(f: () => String,deps: Observable*) = createStringBinding(f, deps: _*)
  }
 
  implicit def ObjectBind[T <: AnyRef ]: BindingBuilder[T,ObjectBinding[T]] = new BindingBuilder[T,ObjectBinding[T]] {
    def createBinding(f: () => T,deps: Observable*) = createObjectBinding(f, deps: _*)
  }
 
  implicit object IntegerBind extends BindingBuilder[Int,IntegerBinding]{
    def createBinding(f: () => Int,deps: Observable*) = createIntegerBinding(f, deps: _*)
  }
  
  implicit object FloatBind extends BindingBuilder[Float,FloatBinding]{
    def createBinding(f: () => Float,deps: Observable*) = createFloatBinding(f, deps: _*)
  }
  
  implicit object DoubleBind extends BindingBuilder[Double,DoubleBinding]{
    def createBinding(f: () => Double,deps: Observable*) = createDoubleBinding(f, deps: _*)
  }
 
  implicit class ObservableValueOps[A](ov: ObservableValue[A,_]) {
    def map[B,Bind](f: A => B)(implicit bb: BindingBuilder[B,Bind]): Bind = {
      bb.createBinding({() =>f(ov.value)},ov)
    }
    def zip[B](other: ObservableValue[B,_]) = createObjectBinding({()=> (ov.value,other.value)}, ov,other)
  }
  
  implicit class PropertyOps[A,J](prop: Property[A,J]){
    def asListener[T](f: T => A):(T=>Unit) = { t: T =>
        runInFxThread(prop.update(f(t)))
    }
  }
  def runInFxThread[A](f: => A) {
      if(Platform.isFxApplicationThread){
        f
      } else {
        Platform.runLater(f)
      }
  }
}