package alexsmirnov.scalafx
import scalafx.Includes._
import scalafx.scene.control.Button
import scalafx.scene.shape.Shape
import scalafx.scene.shape.Polygon
import scalafx.beans.property.ObjectProperty

object ArrowButton {
  case class Dimensions(headWidth: Double, headLength: Double, shaftWidth: Double, shaftLength: Double)

  sealed trait Direction {
    def shape(dim: Dimensions): Shape
    def width(dim: Dimensions): Double
    def height(dim: Dimensions): Double
  }
  case object Right extends Direction {
    def shape(dim: Dimensions): Shape = {
      val Dimensions(hw, hl, sw, sl) = dim
      val hWing = (hw - sw) / 2
      Polygon(0, hWing,
        0, sw + hWing,
        sl, sw + hWing,
        sl, hw,
        sl + hl, hw / 2,
        sl, 0,
        sl, hWing)
    }
    def width(dim: Dimensions) = dim.headLength+dim.shaftLength
    def height(dim: Dimensions) = dim.headWidth
  }
  case object Left extends Direction {
    def shape(dim: Dimensions): Shape = {
      val Dimensions(hw, hl, sw, sl) = dim
      val hWing = (hw - sw) / 2
      Polygon(sl + hl, hWing,
        sl + hl, sw + hWing,
        hl, sw + hWing,
        hl, hw,
        0, hw / 2,
        hl, 0,
        hl, hWing)
    }
    def width(dim: Dimensions) = dim.headLength+dim.shaftLength
    def height(dim: Dimensions) = dim.headWidth
  }
  case object Up extends Direction {
    def shape(dim: Dimensions): Shape = {
      val Dimensions(hw, hl, sw, sl) = dim
      val hWing = (hw - sw) / 2
      Polygon(hw/2,0,
          0,hl,
          hWing,hl,
          hWing,hl+sl,
          hWing+sw,hl+sl,
          hWing+sw,hl,
          hw,hl)
    }
    def width(dim: Dimensions) = dim.headWidth
    def height(dim: Dimensions) = dim.headLength+dim.shaftLength
  }
  case object Down extends Direction {
    def shape(dim: Dimensions): Shape = {
      val Dimensions(hw, hl, sw, sl) = dim
      val hWing = (hw - sw) / 2
      Polygon(hWing,0,
          hWing,sl,
          0,sl,
          hw/2,hl+sl,
          hw,sl,
          hWing+sw,sl,
          hWing+sw,0
          )
    }
    def width(dim: Dimensions) = dim.headWidth
    def height(dim: Dimensions) = dim.headLength+dim.shaftLength
  }

  def arrowShape(headWidth: Double, headLength: Double, shaftWidth: Double, shaftLength: Double): Shape = {
    val headWing = (headWidth - shaftWidth) / 2
    Polygon(0, headWing,
      0, shaftWidth + headWing,
      shaftLength, shaftWidth + headWing,
      shaftLength, headWidth,
      shaftLength + headLength, headWidth / 2,
      shaftLength, 0,
      shaftLength, headWing)
  }
}
class ArrowButton(label: String, dim: ArrowButton.Dimensions, dir: ArrowButton.Direction) extends Button(label) {
  import ObservableImplicits._
  val dimensions = ObjectProperty(dim)
  shape <== dimensions.map(dir.shape(_).delegate)
  clip <== dimensions.map(dir.shape(_).delegate)
  minWidth <== dimensions.map(dir.width)
  minHeight <== dimensions.map(dir.height)
  prefWidth <== dimensions.map(dir.width)
  prefHeight <== dimensions.map(dir.height)
}