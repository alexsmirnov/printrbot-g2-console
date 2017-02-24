package alexsmirnov.stream

import org.reactivestreams.Processor

  abstract class ProcessorBase[A, B](initialRequest: Long = 1L) extends Processor[A, B] with PublisherBase[B] with SubscriberBase[A] {
    def onStart() { request(initialRequest) }
    def onStop() { cancel() }

    def onComplete() = {
      sendComplete()
    }
    def onError(t: Throwable) {
      stopProducer()
      sendError(t)
    }
  }
