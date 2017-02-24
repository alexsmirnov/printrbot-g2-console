package alexsmirnov.stream

import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.reactivestreams.Processor
import java.util.concurrent.LinkedBlockingQueue

  class AsyncProcessor[A](queueSize: Int = 100) extends Processor[A, A] with PublisherBase[A] with SubscriberBase[A] {

    private[this] val executor = Executors.unconfigurableExecutorService(
      new ThreadPoolExecutor(1, 1,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue[Runnable](queueSize + 1),
        streamsThreadFactory))

    def onStart() { request(queueSize) }
    def onStop() { cancel() }
    def onNext(a: A) {
      executor.submit(new Runnable { def run() { sendNext(a); request(1L) } })
    }
    def onComplete() = {
      executor.submit(new Runnable { def run() { sendComplete() } })
    }
    def onError(t: Throwable) {
      stopProducer()
      sendError(t)
    }
  }
