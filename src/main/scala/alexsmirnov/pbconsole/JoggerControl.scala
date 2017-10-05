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

class JoggerControl {
  val h = 150f
  val s = 300f
  val arrowDim = ArrowButton.Dimensions(50,40,30,80)
  val ArrowBtn = List("arrow","button")
  val xAxis = "xAxis" :: ArrowBtn
  val yAxis = "yAxis" :: ArrowBtn
  val zAxis = "zAxis" :: ArrowBtn
  val node: Node = {
    val home = new Button {
          maxWidth = 50
          maxHeight = 50
          shape = Circle(25)
          graphic = new ImageView {
            image = new Image(this.getClass.getResourceAsStream("/images/3dHome.png"),50,50,true,true)
            margin = Insets(5)
          }
    }
    new HBox { root =>
      children = List(
          new VBox(60) {
            alignment = Pos.CenterRight
            children = List(
                new ArrowButton("+Y",arrowDim,ArrowButton.Left){rotate = 30;styleClass=yAxis}, 
                new ArrowButton("-X",arrowDim,ArrowButton.Left){rotate = -30;styleClass=xAxis}
                )
          },
          new VBox(20) {
            alignment = Pos.Center
            children = List(new ArrowButton("+Z",arrowDim,ArrowButton.Up){styleClass=zAxis},
                home,
                new ArrowButton("-Z",arrowDim,ArrowButton.Down){styleClass=zAxis}
                )
          },
          new VBox(60) {
            alignment = Pos.CenterLeft
            children = List(new ArrowButton("+X",arrowDim,ArrowButton.Right){rotate = -30;styleClass=xAxis},
                new ArrowButton("-Y",arrowDim,ArrowButton.Right){rotate = 30;styleClass=yAxis})
          }
          )
    }
  }
}