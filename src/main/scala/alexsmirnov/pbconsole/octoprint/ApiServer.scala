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
import java.util.logging.Logger

class ApiServer(printer: PrinterModel, job: JobModel, config: Settings) extends FilesRoute with JobRoute with PrinterRoute {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  val LOG = Logger.getLogger(this.getClass.getCanonicalName)
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val fRoute = filesRoute(job,config)
  val jRoute = jobRoute(job,printer)
  val pRoute = printerRoute(job, printer)
  val route =
    pathPrefix("api") {
      fRoute ~
      jRoute ~
      pRoute ~
        extractLog { log =>
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
      .flatMap { http =>
        LOG.info("Unbind Http server")
        http.unbind()
      } // trigger unbinding from the port
      .onComplete { _ =>
        LOG.info("Terminate Actor system")
        system.terminate().onComplete { _ =>
          LOG.info("Actor system terminated")
        }
      } // and shutdown when done
  }
}