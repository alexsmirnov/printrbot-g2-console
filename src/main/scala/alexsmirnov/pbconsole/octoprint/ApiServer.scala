package alexsmirnov.pbconsole.octoprint
import spray.json.DefaultJsonProtocol._
import alexsmirnov.pbconsole.Settings
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.print.JobModel
import spray.json._
import java.util.logging.Logger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

class ApiServer(printer: PrinterModel, job: JobModel, config: Settings)  {
  val LOG = Logger.getLogger(this.getClass.getCanonicalName)

    val server = new Server(5000)
    val context = new WebAppContext()
    context setContextPath "/"
//    context.setResourceBase("src/main/webapp")
    context.addEventListener(new ScalatraBootstrap(printer,job,config))
    context.addServlet(classOf[DefaultServlet], "/")

    server.setHandler(context)

    server.start
  def stop() {
    server.stop()
  }
}