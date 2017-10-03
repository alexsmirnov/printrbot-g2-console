package alexsmirnov.scalafx

import scalafx.Includes._
import scalafx.scene.Group
import scalafx.scene.Node
import scalafx.scene.transform.Rotate
import scalafx.scene.transform.Scale
import scalafx.scene.transform.Translate

class Xform(nodes: Node *) extends Group(nodes: _*) {
  val rx = new Rotate { axis = Rotate.XAxis }
  val ry = new Rotate { axis = Rotate.YAxis }
  val rz = new Rotate { axis = Rotate.ZAxis }
  val s = new Scale
  val t = new Translate
  transforms = List(t,rz,ry,rx,s)
}