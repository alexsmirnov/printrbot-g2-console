package alexsmirnov.pbconsole

import org.scalatest.FlatSpec
import org.scalatest.concurrent.Eventually
import java.io.File
import java.io.FileOutputStream
import scala.concurrent.Promise
import org.scalatest.AsyncFlatSpec
import org.scalatest.compatible.Assertion
import java.util.concurrent.TimeoutException
import scala.concurrent.Future

class FileWatcherTest extends AsyncFlatSpec with Eventually {
  "file watcher" should "call listener on file change" in {
    val temp = new File("target/watcher.txt") //File.createTempFile("test", "txt")
      val out = new FileOutputStream(temp)
      out.write('a')
      out.close()
      @volatile
      var changed = Promise[Boolean]
      FileWatcher(temp.toURL(), { () => changed.success(true) })
      Thread.sleep(1500)
      val outChange = new FileOutputStream(temp)
      outChange.write('b')
      outChange.close()
      val futureAssert = changed. future. map (assert(_))
      futureAssert.onComplete{_ => temp.delete() }
      futureAssert
  }
}