package alexsmirnov.stream

import org.reactivestreams.Publisher

class Merge[A] extends PublisherBase[A] { self =>
  private[this] var branches: List[BranchSubscriber] = Nil
  class BranchSubscriber extends SubscriberBase[A] {
    def onNext(a: A) { self.sendNext(a); request(1L) }
    def onComplete() { self.sendComplete() }
    def onError(t: Throwable) { self.sendError(t) }
  }
  
  def addPublisher(p: Publisher[A]) {
    val sub = new BranchSubscriber
    p.subscribe(sub)
    branches = sub +: branches
  }
  override def onStart() = branches.foreach { _.request(1) }
  override def onStop() = branches.foreach { _.cancel() }
} 
