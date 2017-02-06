package alexsmirnov.stream

import scala.collection.JavaConverters._
import org.scalatest.FlatSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.prop.PropertyChecks
import org.reactivestreams._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ProcessorTest extends FlatSpec with Eventually with PropertyChecks {
  class SubscriberProbe[A] extends Subscriber[A] {
    private val _buffer = new ConcurrentLinkedQueue[A]()
    private val _completes = new AtomicInteger
    private val _errors = new ConcurrentLinkedQueue[Throwable]()
    var subscription: Subscription = null
    // implementation
    def onSubscribe(s: Subscription) {subscription = s}
    def onNext(a: A) = {_buffer.add(a)}
    def onComplete() { _completes.incrementAndGet() }
    def onError(e: Throwable) { _errors.add(e) }
    // control methods
    def received = _buffer.asScala
    def errors = _errors.asScala
    def cancel = subscription.cancel()
    def request(n: Long) = subscription.request(n)
    def completed = _completes.get
  }
  "FlatMap" should "request first event on request from subscriber" in {
    
  }
  it should "transform input to collections and send them to subscriber" in {
    
  }
  it should "pass cancel event back to producer and stop transmission" in {
    
  }
  it should "forget remaining events on cancel" in {
    
  }
  it should "start new stream after cancel on first request from subscriber" in {
    
  }
}