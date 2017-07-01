package alexsmirnov.pbconsole.octoprint

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives
import spray.json.DefaultJsonProtocol._
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import akka.http.scaladsl.server.{ Directive1, MissingFormFieldRejection }
import akka.http.scaladsl.model.{ ContentType, Multipart }
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import akka.stream.scaladsl._
import akka.http.scaladsl.server.directives.FileInfo
import scalafx.application.Platform
import scala.concurrent.Promise
import alexsmirnov.pbconsole.PrinterModel.Heater

object PrinterRoute extends Directives with SprayJsonSupport with DefaultJsonProtocol {
  case class TemperatureData(actual: Double,target: Double)
  implicit val tdFormat = jsonFormat2(TemperatureData)
  case class TemperatureState(tool0: TemperatureData,bed: TemperatureData)
  implicit val tsFormat = jsonFormat2(TemperatureState)
  case class StateFlags(operational: Boolean,paused: Boolean,printing: Boolean,ready: Boolean)
  implicit val sfFormat = jsonFormat4(StateFlags)
  case class PrinterState(text: String, flags: StateFlags)
  implicit val psFormat = jsonFormat2(PrinterState)
  case class StateResponse(temperature: TemperatureState,state: PrinterState)
  implicit val srFormat = jsonFormat2(StateResponse)
  def temperatureData(temp: PrinterModel.Heater) = TemperatureData(temp.temperature(),temp.target())
}
trait PrinterRoute {
  import PrinterRoute._
  def printerRoute(job: JobModel, printer: PrinterModel) = {

    path("printer") {
      extractLog { log =>
        get {
          val text = if(printer.connected()){if(job.jobActive()) "Printing" else "Operational"} else "Disconnected"
          val flags = StateFlags(printer.connected(),job.jobPaused(),job.jobActive(),printer.connected() && !job.jobActive())
          val tempState = TemperatureState(temperatureData(printer.extruder),temperatureData(printer.bed))
          complete(StateResponse(tempState,PrinterState(text,flags)))
        }
      }
    }
  }
}