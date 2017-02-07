package alexsmirnov.stream

import org.reactivestreams._
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ReactiveOps {

  def transform[A, B](pub: Publisher[A], proc: Processor[A, B]): Publisher[B] = { pub.subscribe(proc); proc }
  
  trait SyncPublisher[A] extends Publisher[A] {

    private[this] var requested = 0L
    private[this] var started = true
    private[this] val lock = new ReentrantLock(true)
    private[this] val hasRequested = lock.newCondition()
    // Publisher part
    private[this] var subscriber: Subscriber[_ >: A] = null
    class SubscriptionImpl extends Subscription {
      def cancel() = stopProducer()
      def request(n: Long) = doSync {
        if (!started) {
          requested = n
          started = true
          onStart()
        } else {
          requested += n
          hasRequested.signalAll()
        }
      }
    }

    def subscribe(sub: Subscriber[_ >: A]) = {
      require(subscriber == null, "Producer already has subscriber")
      subscriber = sub
      subscriber.onSubscribe(new SubscriptionImpl)
    }

    def sendNext(b: A): Boolean = doSync {
      require(subscriber != null, "Producer has no subscriber")
      while (requested == 0L && started) hasRequested.await()
      if (started) {
        subscriber.onNext(b)
        requested -= 1L
      }
      started
    }
    def sendComplete() {
      require(subscriber != null, "Producer has no subscriber")
      subscriber.onComplete()
    }
    def sendError(t: Throwable) {
      require(subscriber != null, "Producer has no subscriber")
      subscriber.onError(t)
    }

    def onStart(): Unit
    def onStop(): Unit

    def stopProducer() = doSync {
        if(started){
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
  
  trait SubscriberBase[A] extends Subscriber[A] {
    private[this] var subscription: Subscription = null
    private[this] val singleExecutor = Executors.newSingleThreadExecutor()
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
  abstract class ProcessorBase[A,B](initialRequest: Long = 1L) extends Processor[A, B] with SyncPublisher[B] with SubscriberBase[A] {
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
  
  class Fold[A, B,C](zero: => C,f: (A,C) => Either[C,B],finish: C => B) extends ProcessorBase[A, B]() {
    private[this] var buffer: Option[C] = None
    override def onStart() {buffer=None;super.onStart()}
    def onNext(a: A) {
      val acc = buffer.fold(f(a,zero))(f(a,_))
      acc match {
        case Left(buff) => buffer = Some(buff)
        case Right(result) => sendNext(result);buffer = None
      }
      request(1L)
    }
    override def onComplete() = {
      buffer.foreach{ b => sendNext(finish(b))}
      sendComplete()
    }
  }

  class FlatMap[A, B](f: A => Traversable[B]) extends ProcessorBase[A, B]() {
    // Subscriber part
    def onNext(a: A) {
      f(a).forall(sendNext(_))
      request(1L)
    }
  }
  
  class Sync[A](size: Long) extends ProcessorBase[A, A](size) {
    // Subscriber part
    def onNext(a: A) {
      sendNext(a)
    }
  }
  
  def flatMap[A, B](f: A => Traversable[B]) = new FlatMap(f)
  
  def collect[A,B](pf: PartialFunction[A,B]) = new FlatMap(pf.lift.andThen(_.toTraversable))
  
}