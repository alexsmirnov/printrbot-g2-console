package alexsmirnov.pbconsole

import scalafx.scene.Node
import scalafx.scene.layout.BorderPane
import scalafx.scene.layout.VBox
import alexsmirnov.pbconsole.print.JobModel
import alexsmirnov.pbconsole.print.Job
import scalafx.scene.layout.HBox

class PrinterControl(printer: PrinterModel, jobModel: JobModel, settings: Settings) {
  
  val temperature = new TemperatureControl(printer)
  val jogger = new Jogger(printer,settings)
  val job = new Job(jobModel, settings)
  val node: Node = new HBox {
    children = List(jogger.node, temperature.node, job.node)
  }
}