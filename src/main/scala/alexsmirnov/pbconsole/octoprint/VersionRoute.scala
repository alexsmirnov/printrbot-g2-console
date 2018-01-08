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

object VersionRoute extends DefaultJsonProtocol {
  case class VersionResponse(api: String, server: String)
  implicit val vrFormat = jsonFormat2(VersionResponse)
}
class VersionRoute extends ScalatraServlet with SprayJsonSupport {
  import VersionRoute._

  get("/") {
    VersionResponse("0.1", "0.01").toJson
  }
}