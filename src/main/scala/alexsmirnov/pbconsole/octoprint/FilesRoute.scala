package alexsmirnov.pbconsole.octoprint

import java.io.File
import org.slf4j.LoggerFactory

import org.scalatra.Created
import org.scalatra.ScalatraServlet
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.servlet.MultipartConfig

import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.print.JobModel
import scalafx.application.Platform
import spray.json.DefaultJsonProtocol
import spray.json.pimpAny

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
  def fileEntity(name: String) = FileEntity(name, name, Ref(location(name)))
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
  configureMultipartHandling(MultipartConfig(maxFileSize = Some(30 * 1024 * 1024)))

  import FilesRoute._
  val LOG = LoggerFactory.getLogger(this.getClass.getCanonicalName)
  def filesResponse() = {
    // all files
    val uploadFolder = new File(settings.uploadFolder())
    val files = uploadFolder.listFiles().filterNot { f => f.isDirectory() || f.isHidden() }
    FilesResponse(files.map { f =>
      val name = f.getName
      fileEntity(name)
    }.toList)
  }
  get("/") { filesResponse().toJson }
  get("/local") { filesResponse().toJson }
  post("/local") {
    fileParams.get("file").map { file =>
      //                  formFields('select.as[Boolean] ? false, 'print.as[Boolean] ? false) {
      //                    case (select, print) =>
//      val select = params.getOrElse("select", "false").toBoolean
//      val print = params.getOrElse("print", "false").toBoolean
      val fileName = file.name
      val loc = location(fileName)
      LOG.info(s"Upload request to files/local service, params $params , file ${fileName}")
      // move to upload folder, select and print if requested
      val uploadFolder = new File(settings.uploadFolder())
      if (!uploadFolder.exists()) {
        uploadFolder.mkdirs()
      }
      val destPath = new File(uploadFolder, fileName)
      file.write(destPath)
      LOG.info(s"Saved file as ${destPath.getAbsolutePath}")
      Platform.runLater(job.updateFile(destPath))
      Created(UploadResponse(UploadedFiles(fileEntity(fileName))).toJson,
          Map("Content-Type"-> "application/json","Location"->location(fileName)))
      // No upload file, ignoring
    }.getOrElse("")
  }
}