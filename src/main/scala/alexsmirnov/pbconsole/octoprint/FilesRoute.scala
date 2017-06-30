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

object FilesRoute extends Directives with SprayJsonSupport with DefaultJsonProtocol {
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

  sealed trait FormPart
  case class ParamPart(name: String, value: String) extends FormPart
  case class FilePart(metadata: FileInfo, file: File) extends FormPart
  case class ReqData(params: Map[String, String], file: Option[FilePart])

  val fileUploadForm: Directive1[ReqData] = {
    entity(as[Multipart.FormData]).flatMap { formData ⇒
      extractRequestContext.flatMap { ctx ⇒
        implicit val mat = ctx.materializer
        implicit val ec = ctx.executionContext
        val parts = formData.parts.map { part =>
          if (part.filename.isDefined) {
            val destination = File.createTempFile("akka-http-upload", ".tmp")
            val fileInfo = FileInfo(part.name, part.filename.get, part.entity.contentType)
            part.entity.dataBytes.runWith(FileIO.toPath(destination.toPath())).map(_ => FilePart(fileInfo, destination))
          } else {
            part.entity.toStrict(20.seconds).map(e => ParamPart(part.name, e.data.utf8String))
          }
        }
        val fold = parts.runFoldAsync(ReqData(Map.empty, None)) {
          case (acc, el) =>
            el map {
              case f: FilePart => acc.copy(file = Some(f))
              case ParamPart(k, v) => acc.copy(params = acc.params + (k -> v))
            }
        }
        onSuccess(fold)
      }
    }
  }
}
trait FilesRoute {
  import FilesRoute._
  def filesRoute(job: JobModel, settings: Settings) = {
    def filesResponse() = {
      // all files
      val uploadFolder = new File(settings.uploadFolder())
      val files = uploadFolder.listFiles().filterNot { f => f.isDirectory() || f.isHidden() }
      FilesResponse(files.map { f =>
        val name = f.getName
        fileEntity(name)
      }.toList)
    }
    pathPrefix("files") {
      extractLog { log =>
        pathEnd {
          get {
            // all files
            complete(filesResponse())
          }
        } ~
          path("local") {
            get {
              // all files
              complete(filesResponse())
            } ~
              post {
                fileUploadForm {
                  case ReqData(fields, Some(FilePart(metadata, file))) =>
                    //                  formFields('select.as[Boolean] ? false, 'print.as[Boolean] ? false) {
                    //                    case (select, print) =>
                    val select = fields.getOrElse("select", "false").toBoolean
                    val print = fields.getOrElse("print", "false").toBoolean
                    val fileName = metadata.fileName
                    val loc = location(fileName)
                    log.info(s"Upload request to files/local service, params $fields , file ${file.getAbsolutePath}, ${metadata}")
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
                  case ReqData(fields, None) =>
                    complete("")
                }
              }
          }
      }
    }
  }
}