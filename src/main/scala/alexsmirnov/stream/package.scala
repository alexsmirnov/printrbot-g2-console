package alexsmirnov
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ThreadFactory

package object stream {
  
  val streamsThreadFactory = new ThreadFactory {
        val count = new AtomicInteger
        def newThread(r: Runnable) = {
          val t = new Thread(r)
          t.setName("Stream-"+count.incrementAndGet())
          t.setDaemon(true)
          t
        }
  }
}