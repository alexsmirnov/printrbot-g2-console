package alexsmirnov.stream

import org.reactivestreams.Subscription

  class ListenerSubscriber[A](f: A => Unit) extends SubscriberBase[A] {
    override def onSubscribe(s: Subscription) {
      super.onSubscribe(s)
      request(Long.MaxValue)
    }
    def onNext(a: A) { f(a); request(1L) }
    def onComplete() {}
    def onError(t: Throwable) {}
  }
  