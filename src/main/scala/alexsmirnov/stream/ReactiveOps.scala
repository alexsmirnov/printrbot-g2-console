package alexsmirnov.stream

import org.reactivestreams._
import java.util.concurrent.locks.ReentrantLock

object ReactiveOps {
  
  def transform[A,B](pub: Publisher[A],proc: Processor[A,B]): Publisher[B] =  {pub.subscribe(proc);proc}
  abstract class ProcessorBase[A,B] extends Processor[A,B] {
    private[this] var requested = 0L
    private[this] var stopped = true
    private[this] val lock = new ReentrantLock(true)
    private[this] val hasRequested = lock.newCondition()
    // Publisher part
    private[this] var subscriber: Subscriber[_ >: B] = null
    class SubscriptionImpl extends Subscription {
      def cancel() = ???
      def request(n: Long) = ???
    }
    
    def subscribe(sub: Subscriber[_ >: B]) = {
      subscriber = sub
      subscriber.onSubscribe(new SubscriptionImpl)
    }
    
    def sendNext(b: B): Boolean = doSync {
      while(requested == 0L && !stopped) hasRequested.await()
      if(!stopped){
        subscriber.onNext(b)
        requested -= 1L
      }
      stopped
    }
    
    def start()
    
    def stop()
    
    private def doSync[T](code: => T): T = { 
      lock.lockInterruptibly()
      try {
        code
      } finally {
        lock.unlock()
      }
    }
    // Subscriber part
    def onSubscribe(s: Subscription) = ???
    def onNext(a: A) = ???
    def onComplete() = ???
    def onError(t: Throwable) = ???
  }
  
}