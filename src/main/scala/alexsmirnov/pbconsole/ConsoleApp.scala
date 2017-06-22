/*
 * Copyright (c) 2011-2015, ScalaFX Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the ScalaFX Project nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SCALAFX PROJECT OR ITS CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package alexsmirnov.pbconsole

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.{ VBox, HBox, BorderPane }
import scalafx.scene.paint.Color._
import scalafx.scene.paint._
import scalafx.scene.text.Text
import scalafx.scene.Node
import scalafx.scene.control.{ Button, Slider, ToolBar, Tab, TabPane }
import scalafx.scene.control.ListView
import scalafx.scene.control.TextField
import scalafx.scene.layout.Priority
import scalafx.stage.StageStyle
import scalafx.concurrent.ScheduledService
import java.util.concurrent.atomic.AtomicInteger
import scalafx.concurrent.Task
import scalafx.util.Duration
import scalafx.concurrent.WorkerStateEvent
import scalafx.beans.property.{ BooleanProperty, StringProperty, IntegerProperty }
import alexsmirnov.pbconsole.serial.Port
import scalafx.application.Platform
import scalafx.scene.control.Accordion
import scalafx.scene.control.TitledPane
import alexsmirnov.pbconsole.print.Job
import alexsmirnov.pbconsole.print.JobModel

/**
 * TODO: reconnect button, status from {sr:...}, movement control
 * @author asmirnov
 *
 */
object ConsoleApp extends JFXApp {

  
  val settings = new Settings()

  val printer = Printer(parameters.named)

  
  val printerModel = new PrinterModel(printer)
  
  val console = new Console(printerModel)
  val printerControl = new PrinterControl(printerModel)
  val jobModel = new JobModel(printerModel)
  val job = new Job(jobModel,settings)

  stage = new PrimaryStage {
    width = 1000
    height = 700
    initStyle(StageStyle.Unified)
    title = "Printrbot G2 console"
    scene = new Scene {
      fill = Color.rgb(38, 38, 38)
      stylesheets += this.getClass.getResource("/console.css").toExternalForm
      root = new BorderPane {
        top = toolbar
        center = tabs
        bottom = status
      }
    }
  }

  def toolbar: Node = {
    new VBox {
      vgrow = Priority.Always
      hgrow = Priority.Always
      children = new ToolBar {
        content = List(
          new Button {
            text = "Button 1"
          }, new Button {
            text = "Button 2"
          }, new Slider {

          })
      }
    }
  }

  def tabs: Node = {
    new Accordion {
      vgrow = Priority.Always
      hgrow = Priority.Always
      panes = Seq(
        new TitledPane {
          text = "Printer control"
          content = printerControl.node
        },
        new TitledPane {
          text = "Print"
//          closable = false
          content = job.node
        },
          new TitledPane {
          hgrow = Priority.Always
          text = "Console"
//          closable = false
          content = console.node
        })
    }
  }
  
  def status: Node = {
    new HBox {
      hgrow = Priority.Always
      children = new Text {
        text <== printerModel.status
      }
    }
  }

  override def stopApp() {
    printer.stop()
  }

  val counter = new AtomicInteger()
  val scheduler = ScheduledService(Task { "{r:${counter.incrementAndGet} }" })
  scheduler.period = Duration(10000.0)
//  scheduler.onSucceeded = { ev: WorkerStateEvent => console.addInput(scheduler.lastValue()) }
  //  scheduler.start()
  printer.start()
}
