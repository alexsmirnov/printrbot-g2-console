package alexsmirnov.stream

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import scala.concurrent.Future

  trait SubscriberBase[A] extends Subscriber[A] {
    private[this] var subscription: Subscription = null
    private[this] val singleExecutor = Executors.newSingleThreadExecutor(streamsThreadFactory)
    private implicit val execContext = ExecutionContext.fromExecutorService(singleExecutor)
    def onSubscribe(s: Subscription) = {
      require(subscription == null, "Subscriber already has subscription")
      subscription = s
    }
    def request(n: Long) {
      require(subscription != null, "Subscriber has no subscription")
      Future(subscription.request(n))
    }
    def cancel() {
      require(subscription != null, "Subscriber has no subscription")
      Future(subscription.cancel())
    }

    /*
    def onNext(a: B) = ???
    def onComplete() = ???
    def onError(t: Throwable) = ???
    */
  }
