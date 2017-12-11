package alexsmirnov.pbconsole

import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

import scala.collection.JavaConverters._
import java.net.URL
import java.io.File
import java.nio.file.FileSystem
import com.sun.nio.file.SensitivityWatchEventModifier
import java.nio.file.WatchEvent
import java.util.logging.Logger

class FileWatcher(path: Path, file: String, onChange: () => Unit) extends Runnable {
  override def run() {
    val watchService = path.getFileSystem().newWatchService()
    try {
      path.register(
        watchService,
        Array[WatchEvent.Kind[_]](StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE),
        SensitivityWatchEventModifier.HIGH)
      var valid = true
      while (valid) {
        val watchKey = watchService.take()
        watchKey.pollEvents().asScala.foreach { e =>
          val event_path = e.context().asInstanceOf[Path]
          if (event_path.toString() == file) {
            FileWatcher.LOG.info("call listiner for file " + event_path)
            onChange()
          }
        }
        if (!watchKey.reset()) {
          watchKey.cancel()
          valid = false
        }
      }
    } catch {
      case ie: InterruptedException => println("InterruptedException: " + ie)
      case ioe: IOException => println("IOException: " + ioe)
      case e: Exception => println("Exception: " + e)
    } finally {
      FileWatcher.LOG.info("Watch service finished")
      watchService.close()
    }
  }
}

object FileWatcher {
  val LOG = Logger.getLogger("alexsmirnov.pbconsole.FileWatcher")
  def apply(resource: URL, listener: () => Unit) = {
    if (resource.getProtocol.equalsIgnoreCase("file")) {
      val file = new File(resource.toURI())
      val fileName = file.getName
      val dir = file.getParentFile.toPath()
      val thread = new Thread(new FileWatcher(dir, fileName, listener))
      thread.setName("FileWatcher-" + fileName)
      thread.setDaemon(true)
      thread.start()
      LOG.info(s"Watch service for file $file in $dir started")
    } else {
      LOG.info(s"resource ${resource.toExternalForm()} is not file, ignored")
    }
  }
}