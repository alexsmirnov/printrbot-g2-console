package alexsmirnov.stream

import org.reactivestreams._
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors.FinalizableDelegatedExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object ReactiveOps {
  

  def transform[A, B](pub: Publisher[A], proc: Processor[A, B]): Publisher[B] = { pub.subscribe(proc); proc }

  






  class Listener[A](f: A => Unit) extends SubscriberBase[A] {
    override def onSubscribe(s: Subscription) {
      super.onSubscribe(s)
      request(Long.MaxValue)
    }
    def onNext(a: A) { f(a); request(1L) }
    def onComplete() {}
    def onError(t: Throwable) {}
  }

  class Barrier[A](size: Long) extends ProcessorBase[A, A](size) { self =>
    // Subscriber part
    def onNext(a: A) {
      sendNext(a)
    }
    val barrier = new Listener[Any]({ _ => self.request(1L) })
  }
  

  def async[A](queueSize: Int = 100) = new AsyncProcessor[A](queueSize)

  def fold[A, B, C](zero: => C, f: (A, C) => Either[C, B], finish: C => B) = new Fold(zero, f, finish)

  def flatMap[A, B](f: A => Traversable[B]) = new FlatMap(f)

  def map[A, B](f: A => B) = new FlatMap({ a: A => List(f(a)) })

  def collect[A, B](pf: PartialFunction[A, B]) = new FlatMap(pf.lift.andThen(_.toTraversable))

  def listener[A](f: A => Unit) = new Listener[A](f)

  def merge[A](p: Publisher[A] *): Publisher[A] = {
    val result = new Merge[A]
    p.foreach(result.addPublisher(_))
    result
  }
}