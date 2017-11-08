package alexsmirnov.pbconsole.octoprint

import scala.concurrent.Promise

import org.scalatra.FutureSupport
import org.scalatra.NoContent
import org.scalatra.NotFound
import org.scalatra.ScalatraServlet

import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import scalafx.application.Platform
import spray.json.DefaultJsonProtocol
import spray.json.pimpString
import scala.concurrent.ExecutionContext
import spray.json.JsValue

object JobRoute extends DefaultJsonProtocol {
  import FilesRoute.fileInfoFormat
  case class Command(
    command: String,
    action: Option[String])
  implicit val commandFormat = jsonFormat2(Command)
  case class JobInfo(file: FilesRoute.FileEntity, estimatedPrintTime: Long)
  implicit val jobInfoFormat = jsonFormat2(JobInfo)
  case class JobProgress(completion: Double, printTime: Long, printTimeLeft: Long)
  implicit val jobProgressFormat = jsonFormat3(JobProgress)
  case class JobResponse(job: JobInfo, progress: JobProgress)
  implicit val jobResponseFormat = jsonFormat2(JobResponse)
}
class JobRoute(job: JobModel, printer: PrinterModel) extends ScalatraServlet with FutureSupport with SprayJsonSupport {
  import JobRoute._
  def executor =  ExecutionContext.Implicits.global
  get("/") {
    if (job.jobActive()) {
      val responsePromise = Promise[JsValue]()
      Platform.runLater {
        val currentStat = job.printService.getValue()
        val printTime = if (null != currentStat) (currentStat.printTimeMinutes * 60.0).toInt else 0
        val totalTime = (job.fileStats().printTimeMinutes * 60.0).toInt
        val fileName = job.gcodeFile().map(_.getName).getOrElse("Unknown")
        val jobInfo = JobInfo(FilesRoute.fileEntity(fileName), totalTime)
        val progress = JobProgress(job.printService.getProgress, printTime, totalTime - printTime)
        responsePromise.success(JobResponse(jobInfo, progress).toJson)
      }
      responsePromise.future
    } else {
      NotFound("No job active")
    }
  }
  post("/") {
    val cmd = request.body.parseJson.convertTo[Command]
    cmd.command match {
      case "start" => Platform.runLater(job.start())
      case "cancel" => Platform.runLater(job.cancel())
    }
    NoContent
  }
}