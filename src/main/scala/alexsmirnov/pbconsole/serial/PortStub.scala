package alexsmirnov.pbconsole.serial

import alexsmirnov.stream.ReactiveOps._
import org.reactivestreams._
import java.util.concurrent.TimeUnit

class PortStub extends Port {
 
    val receiver = toLines
    val processor = map[String,String]{ line =>
      Thread.sleep(1)
      """{"r":{"foo":"bar"},"f":[1,0,8]}"""
    }
    val asyncReceiver = transform(receiver, async[String]())
    val responser = linesToBytes
    transform(asyncReceiver,processor).subscribe(responser)
    
    var listeners:List[Port.StateEvent => Unit] = Nil

  def addStateListener(listener: Port.StateEvent => Unit): Unit = {
    listeners = listener +: listeners
  }

  def run(): Unit = {
    Port.scheduler.schedule(new Runnable{
      def run() {
        val ev = Port.Connected("foo",0)
        listeners.foreach(_(ev))
        receiver.request(100L)
        responser.request(100L)
        responser.requestProducer(100)
        processor.sendNext("""{"r":{"sr":{"state":"READY"}},"f":[1,0,8]}""")
      }
    }, 10, TimeUnit.MILLISECONDS)
  }

  def close(): Unit = {
  }

  def onComplete(): Unit = {
    receiver.onComplete()
  }

  def onError(t: Throwable): Unit = {
    receiver.onError(t)
  }

  def onNext(b: Byte): Unit = {
    receiver.onNext(b)
  }

  def onSubscribe(sub: Subscription): Unit = {
    receiver.onSubscribe(sub)
  }

  def subscribe(s: Subscriber[_ >: Byte]): Unit = {
    responser.subscribe(s)
  }
}