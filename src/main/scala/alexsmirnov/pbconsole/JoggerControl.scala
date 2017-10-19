package alexsmirnov.pbconsole

import scalafx.scene.Node

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.shape.MeshView
import scalafx.scene.shape.TriangleMesh
import scalafx.scene.shape.DrawMode
import scalafx.scene.paint.PhongMaterial
import scalafx.scene.paint.Color
import scalafx.scene.Group
import scalafx.scene.PerspectiveCamera
import scalafx.geometry.Point3D
import scalafx.scene.SubScene
import alexsmirnov.scalafx.Xform
import scalafx.scene.shape.Box
import scalafx.scene.paint.Material
import scalafx.beans.property.DoubleProperty
import scalafx.scene.layout.GridPane
import alexsmirnov.scalafx.ArrowButton
import scalafx.scene.control.Button
import scalafx.scene.image.ImageView
import scalafx.scene.image.Image
import scalafx.geometry.Insets
import scalafx.scene.layout.HBox
import scalafx.scene.layout.VBox
import scalafx.geometry.Pos
import scalafx.scene.shape.Circle
import scalafx.event.ActionEvent
import scalafx.beans.property.BooleanProperty

object JoggerControl {
  val h = 150f
  val s = 300f
  val arrowDim = ArrowButton.Dimensions(50, 40, 30, 60)
  val ArrowBtn = List("arrow", "button")
  val xAxis = "xAxis" :: ArrowBtn
  val yAxis = "yAxis" :: ArrowBtn
  val zAxis = "zAxis" :: ArrowBtn
  sealed abstract class Dir(val axis: String, val sign: Int)
  case object XPlus extends Dir("X", 1)
  case object XMinus extends Dir("X", -1)
  case object YPlus extends Dir("Y", 1)
  case object YMinus extends Dir("Y", -1)
  case object ZPlus extends Dir("Z", 1)
  case object ZMinus extends Dir("Z", -1)
  case object EPlus extends Dir("E", 1)
  case object EMinus extends Dir("E", -1)
}
class JoggerControl extends HBox { root =>
  import JoggerControl._
  val home = new Button {
    maxWidth = 50
    maxHeight = 50
    shape = Circle(25)
    graphic = new ImageView {
      image = new Image(this.getClass.getResourceAsStream("/images/3dHome.png"), 50, 50, true, true)
      margin = Insets(5)
    }
  }
  def onHomeAction(f: => Unit) {
    home.onAction = { ae: ActionEvent => f }
  }
  // Axis actions
  private[this] var axisListeners: List[(Dir, Boolean) => Unit] = Nil
  def onAxisArmed(f: (Dir, Boolean) => Unit) = { axisListeners = f :: axisListeners }
  private def axisArmedChanged(dir: Dir, armed: Boolean) = axisListeners.foreach(_(dir, armed))
  children = List(
    new VBox(50) {
      alignment = Pos.CenterRight
      children = List(
        new ArrowButton("+Y", arrowDim, ArrowButton.Left) { rotate = 30; styleClass = yAxis; armed.onChange(axisArmedChanged(YPlus, armed())) },
        new ArrowButton("-X", arrowDim, ArrowButton.Left) { rotate = -30; styleClass = xAxis; armed.onChange(axisArmedChanged(XMinus, armed())) })
    },
    new VBox(17) {
      alignment = Pos.Center
      children = List(new ArrowButton("+Z", arrowDim, ArrowButton.Up) { styleClass = zAxis; armed.onChange(axisArmedChanged(ZPlus, armed())) },
        home,
        new ArrowButton("-Z", arrowDim, ArrowButton.Down) { styleClass = zAxis; armed.onChange(axisArmedChanged(ZMinus, armed())) })
    },
    new VBox(50) {
      alignment = Pos.CenterLeft
      children = List(new ArrowButton("+X", arrowDim, ArrowButton.Right) { rotate = -30; styleClass = xAxis; armed.onChange(axisArmedChanged(XPlus, armed())) },
        new ArrowButton("-Y", arrowDim, ArrowButton.Right) { rotate = 30; styleClass = yAxis; armed.onChange(axisArmedChanged(YMinus, armed())) })
    })
}