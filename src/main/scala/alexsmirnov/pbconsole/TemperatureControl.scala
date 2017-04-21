package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.scene.chart.XYChart
import scalafx.scene.chart.NumberAxis
import scalafx.collections.ObservableBuffer
import scalafx.scene.chart.LineChart
import scalafx.scene.layout.VBox
import scalafx.util.{ Duration => FXDuration }
import scalafx.concurrent.ScheduledService
import java.util.concurrent.atomic.AtomicInteger
import scalafx.concurrent.Task
import scala.concurrent.Await
import scala.concurrent.duration._
import scalafx.concurrent.WorkerStateEvent
import java.util.function.Predicate
import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.text.Text
import scalafx.scene.text.TextFlow
import scalafx.scene.layout.HBox
import scalafx.scene.control.Button
import scalafx.event.ActionEvent
import scalafx.scene.control.TextFormatter
import scalafx.util.converter.FloatStringConverter
import scalafx.scene.control.TextField
import scalafx.scene.control.Spinner
import scalafx.scene.control.SpinnerValueFactory

class TemperatureControl(printer: PrinterModel) {
  type DT = javafx.scene.chart.XYChart.Data[Number, Number]
  type ST = javafx.scene.chart.XYChart.Series[Number, Number]
  val xAxis = NumberAxis("Seconds", 0, 180, 10)
  xAxis.tickLabelsVisible = false
  xAxis.forceZeroInRange = false
  val yAxis = NumberAxis("Temperature", 0, 300, 25)

  def seconds() = System.currentTimeMillis() / 1000L
  val startSeconds = seconds()
  def time() = (seconds() - startSeconds).toDouble

  // Helper function to convert a temperature to `XYChart.Data`
  def toChartData(time: Double, y: Double) = XYChart.Data[Number, Number](time, y)
  def removeOlderThan(time: Double, buff: ObservableBuffer[DT]) {
    buff.removeIf(new Predicate[DT] {
      def test(dt: DT) = dt.getXValue.doubleValue() < time
    })
  }
  val bedTempData = ObservableBuffer.empty[DT]
  val bedTargetData = ObservableBuffer.empty[DT]
  val extruderTempData = ObservableBuffer.empty[DT]
  val extruderTargetData = ObservableBuffer.empty[DT]

  def series(name: String, data: ObservableBuffer[DT]) = {
    val s = new XYChart.Series[Number, Number]
    s.name_=(name)
    s.data_=(data)
    s
  }

  val allDataSeries = ObservableBuffer[ST](
    series("Extruder", extruderTempData),
    series("Extruder target", extruderTargetData),
    series("Bed", bedTempData),
    series("Bed target", bedTargetData))

  val scheduler = {
    val s = ScheduledService(Task { if (printer.connected()) Await.result(printer.sendQuery("M105", Source.Monitor), 5.seconds) else Nil })
    s.period = FXDuration(2000.0)
    s.delay = FXDuration(2000.0)
    s.restartOnFailure = true
    s.onSucceeded = { ev: WorkerStateEvent =>
      val lv = s.lastValue()
      if (ev.eventType == WorkerStateEvent.WorkerStateSucceeded && null != lv) {
        val ticks = time()
        xAxis.lowerBound = ticks - 180.0
        xAxis.upperBound = ticks
        allDataSeries.foreach { ds => removeOlderThan(ticks, ds.data()) }
        lv.foreach {
          case ExtruderTemp(t) =>
            extruderTempData += toChartData(ticks, t); printer.extruder.temperature.update(t)
          case ExtruderTarget(t) =>
            extruderTargetData += toChartData(ticks, t); printer.extruder.target.update(t)
          case ExtruderOutput(n) => printer.extruder.output.update(n)
          case BedTemp(t) =>
            bedTempData += toChartData(ticks, t); printer.bed.temperature.update(t)
          case BedTarget(t) =>
            bedTargetData += toChartData(ticks, t); printer.bed.target.update(t)
          case BedOutput(n) => printer.bed.output.update(n)
          case _ =>
        }
      }
    }
    s.start()
    s
  }
  val chart = new LineChart[Number, Number](xAxis, yAxis, allDataSeries)
  chart.createSymbols = false

  def tempControl(title: String, heater: PrinterModel.Heater, command: Float => String): Node = {
    val temperatureValueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 300, 0, 5)
    val temperature = new Spinner[Integer] {
      editable = true
      valueFactory = temperatureValueFactory
      disable <== printer.connected.not()
    }
    new BorderPane {
      top = new Text(title)
      center = new TextFlow(
        new Text("Temperature:"),
        new Text {
          text <== heater.temperature.asString("%6.2f\u00B0C")
        },
        new Text(" Target:"),
        new Text {
          text <== heater.target.asString("%6.2f\u00B0C")
        },
        new Text(" Output:"),
        new Text {
          text <== heater.output.asString()
        })
      bottom = new HBox {
        children = List(
          new Button {
            text = "Off"
            onAction = { ae: ActionEvent => printer.sendLine(command(0), Source.Monitor) }
            disable <== printer.connected.not()
          },
          temperature,
          new Button {
            text = "Set"
            disable <== printer.connected.not()
            onAction = { ae: ActionEvent =>
              temperature.increment(0)
              printer.sendLine(command(temperature.value().toFloat), Source.Monitor)
            }
          })
      }
    }
  }
  val node = new VBox {
    children = List(
      chart,
      tempControl("Extruder", printer.extruder, { t => s"M104 S$t" }),
      tempControl("Heated Bed", printer.bed, { t => s"M140 S$t" }))
  }
}