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

object FilesRoute extends Directives with SprayJsonSupport with DefaultJsonProtocol  {
  case class Ref(resource: String)
  implicit val refFormat = jsonFormat1(Ref)
  case class FileInfo(
        name: String,
        path: String,
        refs: Ref,
        `type`: String = "machinecode",
        origin: String = "local"
      )
  implicit val fileInfoFormat = jsonFormat5(FileInfo)
  case class FilesResponse(
      files: List[FileInfo],
      free: String = "100GB")
  implicit val filesResponseFormat = jsonFormat2(FilesResponse)
  case class UploadedFiles(local: FileInfo)
  implicit val uploadedFilesFormat = jsonFormat1(UploadedFiles)
  case class UploadResponse(
      files: UploadedFiles,
      done: Boolean = true)
  implicit val uploadResponseFormat = jsonFormat2(UploadResponse)
}
trait FilesRoute {
  import FilesRoute._
  def filesRoute(settings: Settings) = pathPrefix("files") {
    extractLog { log =>
      pathEnd {
        get {
          // all files
          complete("")
        }
      } ~
        path("local") {
          get {
            // all files
            complete("")
          } ~
            post {
              uploadedFile("file") {
                case (metadata, file) =>
                  formFields('select.as[Boolean] ? false, 'print.as[Boolean] ? false) {
                    case (select, print) =>
                      val fileName = metadata.fileName
                      val location = s"http://localhost:5000/api/files/local/$fileName"
                      log.info(s"Upload request to files/local service select:$select, print:$print, file ${file.getAbsolutePath}, ${metadata}")
                      // move to upload folder, select and print if requested
                      val destPath = Paths.get(settings.uploadFolder()).resolve(fileName)
                      Files.move(file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING)
                      complete( (StatusCodes.Created,
                          List(Location(Uri(location))),
                          UploadResponse(UploadedFiles(FileInfo(fileName,fileName,Ref(location)))))
                      )
                  }
              }
            }
        }
    }
  }
}