package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.layout.BorderPane
import scalafx.scene.paint.Color
import scalafx.stage.StageStyle
import scalafx.scene.layout.VBox
import scalafx.scene.control.Slider

object JoggerApp extends JFXApp {

  val jogger = new JoggerControl

  stage = new PrimaryStage {
    width = 1000
    height = 700
    initStyle(StageStyle.Unified)
    title = "Printrbot G2 console"
    scene = new Scene {
//      fill = Color.rgb(38, 38, 38)
      stylesheets += this.getClass.getResource("/console.css").toExternalForm
      root = new BorderPane {
        center = jogger
        left = new VBox {
          children = List(
              new Slider {
                min = -180
                max = 180
              },
              new Slider {
                min = -180
                max = 180
              },
              new Slider {
                min = -180
                max = 180
              }
              )
        }
      }
    }
  }

}