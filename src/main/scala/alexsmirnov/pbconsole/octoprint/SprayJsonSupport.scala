package alexsmirnov.pbconsole.octoprint

import org.scalatra.servlet.ServletBase
import org.scalatra._
import spray.json._

trait SprayJsonSupport extends ServletBase {

  override def renderResponse(actionResult: Any): Unit = {
    actionResult match {
      case js: JsValue => super.renderResponse(Ok(js.compactPrint, Map("Content-Type" -> "application/json")))
      case ActionResult(status, js: JsValue, headers) => super.renderResponse(ActionResult(status, js.compactPrint, headers))
      case _ => super.renderResponse(actionResult)
    }
  }
}