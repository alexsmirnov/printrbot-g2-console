package alexsmirnov.pbconsole.octoprint

import org.scalatra.LifeCycle
import javax.servlet.ServletContext
import javax.servlet.ServletContextListener
import alexsmirnov.pbconsole.print.JobModel
import alexsmirnov.pbconsole.PrinterModel
import alexsmirnov.pbconsole.Settings
import javax.servlet.ServletContextEvent

class ScalatraBootstrap(printer: PrinterModel, job: JobModel, config: Settings) extends LifeCycle with ServletContextListener {
  private[this] var servletContext: ServletContext = _

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    servletContext = sce.getServletContext
    init(servletContext)
  }

  def contextDestroyed(sce: ServletContextEvent): Unit = {
      destroy(servletContext)
  }
  override def init(context: ServletContext) {
    context mount (new VersionRoute(), "/api/version/*")
    context mount (new FilesRoute(job,config), "/api/files/*")
    context mount (new JobRoute(job,printer), "/api/job/*")
    context mount (new PrinterRoute(job,printer), "/api/printer/*")
  }
}