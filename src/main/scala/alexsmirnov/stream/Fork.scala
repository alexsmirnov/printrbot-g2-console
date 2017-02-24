package alexsmirnov.stream

import org.reactivestreams.Processor
import org.reactivestreams.Subscriber

  class Fork[A] extends Processor[A, A] with SubscriberBase[A] { self =>

    var branches: List[BranchPublisher] = Nil
    class BranchPublisher extends PublisherBase[A] {
      def onStart() { self.request(1L) }
      def onStop() { self.cancel() }
    }

    def subscribe(sub: Subscriber[_ >: A]) {
      val pub = new BranchPublisher()
      pub.subscribe(sub)
      branches = pub +: branches
    }
    def onNext(a: A) = { branches.foreach(_.sendNext(a)); request(1L) }
    def onComplete() = branches.foreach(_.sendComplete())
    def onError(t: Throwable) = branches.foreach(_.sendError(t))
  }
