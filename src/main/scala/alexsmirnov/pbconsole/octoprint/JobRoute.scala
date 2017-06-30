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

object JobRoute extends Directives with SprayJsonSupport with DefaultJsonProtocol {
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
trait JobRoute {
  import JobRoute._
  def jobRoute(job: JobModel, printer: PrinterModel) = {

    path("job") {
      extractLog { log =>
        get {
          if (job.jobActive()) {
            val responsePromise = Promise[JobResponse]()
            Platform.runLater {
              val currentStat = job.printService.getValue()
              val printTime = if(null != currentStat) (currentStat.printTimeMinutes * 60.0).toInt else 0
              val totalTime = (job.fileStats().printTimeMinutes * 60.0).toInt
              val fileName = job.gcodeFile().map(_.getName).getOrElse("Unknown")
              val jobInfo = JobInfo(FilesRoute.fileEntity(fileName), totalTime)
              val progress = JobProgress(job.printService.getProgress, printTime, totalTime - printTime)
              responsePromise.success(JobResponse(jobInfo, progress))
            }
            complete(responsePromise.future)
          } else {
            complete(StatusCodes.NotFound)
          }
        } ~
          post {
            entity(as[Command]) { cmd: Command =>
              cmd.command match {
                case "start" => Platform.runLater(job.start())
                case "cancel" => Platform.runLater(job.cancel())
              }
              complete(StatusCodes.NoContent)
            }

          }
      }
    }
  }
}