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
  def arrow(w: Float,l: Float,h: Float, material: Material) = mesh(
      points(
          (l,h,0),
          (0,h,-w/2),
          (0,h,w/2),
          
          (l,0,0),
          (0,0,-w/2),
          (0,0,w/2)
          ),
      faces(
          // top
          (0,2,1),
          // back
          (2,5,1),
          (5,4,1),
          // left
          (2,3,5),
          (0,3,2),
          // right
          (0,1,4),
          (0,4,3),
          // bottom
          (3,4,5)
          ),
      material)
  val node: Node = {
    val camera = new PerspectiveCamera(true)
    camera.translateZ = -800
    camera.nearClip = 0.1
    camera.farClip = 10000
    val cameraForm = new Xform(camera)
    cameraForm.rx.angle = -15
    cameraForm.ry.angle = 30
    val root = new Group(cameraForm, axis,
      arrow(200,200,10,
        blueMaterial))
    val sscene = new SubScene(root, 400, 400)
    sscene.camera = camera
    sscene
  }
}