package alexsmirnov.stream

  class FlatMap[A, B](f: A => Traversable[B]) extends ProcessorBase[A, B]() {
    // Subscriber part
    def onNext(a: A) {
      f(a).forall(sendNext(_))
      request(1L)
    }
  }
