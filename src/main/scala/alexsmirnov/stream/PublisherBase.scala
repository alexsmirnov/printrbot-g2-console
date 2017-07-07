package alexsmirnov.stream

import java.util.concurrent.locks.ReentrantLock
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import org.reactivestreams.Subscriber
import java.util.concurrent.TimeUnit

  trait PublisherBase[A] extends Publisher[A] { self =>

    private[this] var requested = 0L
    private[this] var started = false
    private[this] val lock = new ReentrantLock(true)
    private[this] val hasRequested = lock.newCondition()
    // Publisher part
    private[this] var subscriber: Subscriber[_ >: A] = null
    
    def getRequested = requested
    
    def isStarted = started

    private class SubscriptionImpl extends Subscription {
      def cancel() = stopProducer()
      def request(n: Long) = requestProducer(n)
    }

    def subscribe(sub: Subscriber[_ >: A]) = {
      require(subscriber == null, "Producer already has subscriber")
      subscriber = sub
      subscriber.onSubscribe(new SubscriptionImpl)
    }

    def sendNext(b: A): Boolean = doSync {
      require(subscriber != null, "Producer has no subscriber")
      while (requested <= 0L && started) hasRequested.await(10, TimeUnit.SECONDS)
      if (started) {
        subscriber.onNext(b)
        requested -= 1L
      }
      started
    }
    
    def offer(a:A): Boolean = doSync {
      if(started && requested > 0) {
        subscriber.onNext(a)
        requested -= 1L
        true
      } else false
    }
    
    def sendComplete() {
      require(subscriber != null, "Producer has no subscriber")
      subscriber.onComplete()
    }
    
    def sendError(t: Throwable) {
      require(subscriber != null, "Producer has no subscriber")
      subscriber.onError(t)
    }

    protected def onStart(): Unit

    protected def onStop(): Unit

    private def requestProducer(n: Long) = doSync {
      if (!started) {
        requested = n
        started = true
        onStart()
      } else {
        requested += n
        hasRequested.signalAll()
      }
    }

    protected def stopProducer() = doSync {
      if (started) {
        started = false;
        requested = 0
        hasRequested.signalAll()
        onStop()
      }
    }

    private def doSync[T](code: => T): T = {
      lock.lockInterruptibly()
      try {
        code
      } finally {
        lock.unlock()
      }
    }
  }

  class SimplePublisher[A] extends PublisherBase[A] {
    
    def onStart() {}

    def onStop() {}
  }
