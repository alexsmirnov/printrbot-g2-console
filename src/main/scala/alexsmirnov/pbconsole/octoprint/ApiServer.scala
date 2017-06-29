package alexsmirnov.pbconsole.octoprint
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

class ApiServer(printer: PrinterModel, job: JobModel, config: Settings) extends FilesRoute {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val fRoute = filesRoute(config)
  val route =
    extractLog { log =>
      pathPrefix("api") {
        fRoute ~
        path(Remaining) { service =>
          extractMethod { method =>
            log.info(s"${method.name} request to service $service")
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
          }
        }
    }
  }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 5000)
  def stop() {
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}