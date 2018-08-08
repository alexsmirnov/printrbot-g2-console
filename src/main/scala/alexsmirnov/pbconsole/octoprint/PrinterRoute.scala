package alexsmirnov.pbconsole.octoprint

import org.scalatra.FutureSupport
import org.scalatra.NoContent
import org.scalatra.NotFound
import org.scalatra.ScalatraServlet

import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import scalafx.application.Platform
import spray.json.DefaultJsonProtocol
import spray.json.pimpString
import alexsmirnov.pbconsole.PrinterModel.Heater

object PrinterRoute extends DefaultJsonProtocol {
  case class TemperatureData(actual: Double, target: Double)
  implicit val tdFormat = jsonFormat2(TemperatureData)
  case class TemperatureState(tool0: TemperatureData, bed: TemperatureData)
  implicit val tsFormat = jsonFormat2(TemperatureState)
  case class StateFlags(operational: Boolean, paused: Boolean, printing: Boolean, ready: Boolean)
  implicit val sfFormat = jsonFormat4(StateFlags)
  case class PrinterState(text: String, flags: StateFlags)
  implicit val psFormat = jsonFormat2(PrinterState)
  case class StateResponse(temperature: TemperatureState, state: PrinterState)
  implicit val srFormat = jsonFormat2(StateResponse)
  def temperatureData(temp: PrinterModel.Heater) = TemperatureData(temp.temperature(), temp.target())
}
class PrinterRoute(job: JobModel, printer: PrinterModel) extends ScalatraServlet with SprayJsonSupport {
  import PrinterRoute._

  get("/") {
    val text = if (printer.connected()) { if (job.jobActive()) "Printing" else "Operational" } else "Disconnected"
    val flags = StateFlags(printer.connected(), job.jobPaused(), job.jobActive(), printer.connected() && !job.jobActive())
    val tempState = TemperatureState(temperatureData(printer.extruders.head), temperatureData(printer.bed))
    StateResponse(tempState, PrinterState(text, flags)).toJson
  }
}