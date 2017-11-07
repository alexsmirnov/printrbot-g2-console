package alexsmirnov.pbconsole.octoprint

import spray.json.DefaultJsonProtocol._
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import spray.json._
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import scalafx.application.Platform
import org.scalatra.ScalatraServlet
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.servlet.MultipartConfig
import java.util.logging.Logger

object FilesRoute extends DefaultJsonProtocol {
  case class Ref(resource: String)
  implicit val refFormat = jsonFormat1(Ref)
  case class FileEntity(
    name: String,
    path: String,
    refs: Ref,
    `type`: String = "machinecode",
    origin: String = "local")
  implicit val fileInfoFormat = jsonFormat5(FileEntity)
    def location(name: String) = s"http://localhost:5000/api/files/local/$name"
  def fileEntity(name: String) = FileEntity(name,name,Ref(location(name)))
  case class FilesResponse(
    files: List[FileEntity],
    free: String = "100GB")
  implicit val filesResponseFormat = jsonFormat2(FilesResponse)
  case class UploadedFiles(local: FileEntity)
  implicit val uploadedFilesFormat = jsonFormat1(UploadedFiles)
  case class UploadResponse(
    files: UploadedFiles,
    done: Boolean = true)
  implicit val uploadResponseFormat = jsonFormat2(UploadResponse)

}
class FilesRoute(job: JobModel, settings: Settings) extends ScalatraServlet with FileUploadSupport with SprayJsonSupport {
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3*1024*1024)))

  import FilesRoute._
  val LOG = Logger.getLogger(this.getClass.getCanonicalName)
    def filesResponse() = {
      // all files
      val uploadFolder = new File(settings.uploadFolder())
      val files = uploadFolder.listFiles().filterNot { f => f.isDirectory() || f.isHidden() }
      FilesResponse(files.map { f =>
        val name = f.getName
        fileEntity(name)
      }.toList)
    }
    get(""){ filesResponse().toJson }
    get("/"){ filesResponse().toJson }
    get("/local"){ filesResponse().toJson }
    post("/local") {
                    //                  formFields('select.as[Boolean] ? false, 'print.as[Boolean] ? false) {
                    //                    case (select, print) =>
                    val select = fields.getOrElse("select", "false").toBoolean
                    val print = fields.getOrElse("print", "false").toBoolean
                    val fileName = metadata.fileName
                    val loc = location(fileName)
                    LOG.info(s"Upload request to files/local service, params $fields , file ${file.getAbsolutePath}, ${metadata}")
                    // move to upload folder, select and print if requested
                    val uploadFolder = new File(settings.uploadFolder())
                    if (!uploadFolder.exists()) {
                      uploadFolder.mkdirs()
                    }
                    val destPath = uploadFolder.toPath().resolve(fileName)
                    Files.move(file.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING)
                    log.info(s"Saved file as ${destPath.toUri()}")
                    Platform.runLater(job.updateFile(destPath.toFile()))
                    complete((StatusCodes.Created,
                      List(Location(Uri(loc))),
                      UploadResponse(UploadedFiles(fileEntity(fileName)))))
                  // No upload file, ignoring
                      ""
  }
}