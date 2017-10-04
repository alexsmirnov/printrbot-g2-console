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

class JoggerControl {
  val h = 150f
  val s = 300f
  def blueMaterial = new PhongMaterial {
    diffuseColor = Color.LightBlue
    specularColor = Color.Blue
  }
  def redMaterial = new PhongMaterial {
    diffuseColor = Color.LightPink
    specularColor = Color.Red
  }
  def greenMaterial = new PhongMaterial {
    diffuseColor = Color.LightGreen
    specularColor = Color.Green
  }
  def axis = new Xform(
    new Box(1000, 5, 5) { material = redMaterial },
    new Box(5, 1000, 5) { material = greenMaterial },
    new Box(5, 5, 1000) { material = blueMaterial })
  def points(pnts: (Float, Float, Float)*) = pnts.flatMap { case (x, y, z) => Seq(x, y, z) }.toArray
  def faces(triangles: (Int, Int, Int)*) = triangles.flatMap { case (x, y, z) => Seq(x, 0, y, 0, z, 0) }.toArray

  def mesh(pnts: Array[Float], fcs: Array[Int], stuff: Material) = {
    val triangleMesh = new TriangleMesh {
      texCoords = Array(0f, 0f)
      points = pnts
      faces = fcs
    }
    new MeshView(triangleMesh) {
      drawMode = DrawMode.Fill
      material = stuff
    }
  }
  val arrowBodyScale = 0.7f
  def arrow(w: Float,l: Float,h: Float, material: Material) = mesh(
      points(
          // top triangle
          (l,h,0),                    // 0
          (0,h,-w/2),
          (0,h,w/2),
          // bottom triangle
          (l,0,0),                    // 3
          (0,0,-w/2),
          (0,0,w/2),
          // top body
          (0,h,-w/2*arrowBodyScale), //6
          (0,h,w/2*arrowBodyScale),
          (-l,h,-w/2*arrowBodyScale), //8
          (-l,h,w/2*arrowBodyScale),
          // top body
          (0,0,-w/2*arrowBodyScale), //10
          (0,0,w/2*arrowBodyScale),
          (-l,0,-w/2*arrowBodyScale), //12
          (-l,0,w/2*arrowBodyScale)
          ),
      faces(
          // top
          (0,2,1),
          // tip back
          (2,7,5),
          (7,11,5),
          (6,1,4),
          (6,4,10),
          // left
          (2,3,5),
          (0,3,2),
          // right
          (0,4,1),
          (0,3,4),
          // bottom
          (3,4,5),
          // body
          // top
          (6,7,8),
          (7,9,8),
          // bottom
          (10,11,12),
          (13,12,11),
          // left
          (6,12,8),
          (6,10,12),
          // Right
          (7,11,9),
          (11,13,9),
          // back
          (8,12,9),
          (12,13,9)
          ),
      material)
  val rx = DoubleProperty(-15)
  val ry = DoubleProperty(30)
  val rz = DoubleProperty(0)
  val node: Node = {
    val camera = new PerspectiveCamera(true)
    camera.translateZ = -800
    camera.nearClip = 0.1
    camera.farClip = 10000
    val cameraForm = new Xform(camera)
    cameraForm.rx.angle <== rx
    cameraForm.ry.angle <== ry
    cameraForm.rz.angle <== rz
    val root = new Group(cameraForm, axis,
      arrow(100,100,20,
        blueMaterial))
    val sscene = new SubScene(root, 600, 600)
    sscene.camera = camera
    sscene
  }
}