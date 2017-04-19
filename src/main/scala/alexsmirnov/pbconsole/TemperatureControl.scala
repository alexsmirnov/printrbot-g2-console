package alexsmirnov.pbconsole

import scalafx.scene.chart.XYChart
import scalafx.scene.chart.NumberAxis
import scalafx.collections.ObservableBuffer
import scalafx.scene.chart.LineChart
import scalafx.scene.layout.VBox

class TemperatureControl {
  val xAxis = NumberAxis("Values for X-Axis", 0, 3, 1)
  val yAxis = NumberAxis("Values for Y-Axis", 0, 3, 1)

  // Helper function to convert a tuple to `XYChart.Data`
  val toChartData = (xy: (Double, Double)) => XYChart.Data[Number, Number](xy._1, xy._2)

  val extruder = new XYChart.Series[Number, Number] {
    name = "Extruder Temperature"
    data = Seq(
      (0.0, 1.0),
      (1.2, 1.4),
      (2.2, 1.9),
      (2.7, 2.3),
      (2.9, 0.5)).map(toChartData)
  }

  val bed = new XYChart.Series[Number, Number] {
    name = "Bed Temperature"
    data = Seq(
      (0.0, 1.6),
      (0.8, 0.4),
      (1.4, 2.9),
      (2.1, 1.3),
      (2.6, 0.9)).map(toChartData)
  }
  val chart = new LineChart[Number, Number](xAxis, yAxis, ObservableBuffer(extruder, bed))
  val node = new VBox {
    children = List(
        chart
    )
  }
}