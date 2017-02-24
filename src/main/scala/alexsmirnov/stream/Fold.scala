package alexsmirnov.stream

  class Fold[A, B, C](zero: => C, f: (A, C) => Either[C, B], finish: C => B) extends ProcessorBase[A, B]() {
    private[this] var buffer: Option[C] = None
    override def onStart() { buffer = None; super.onStart() }
    def onNext(a: A) {
      val acc = buffer.fold(f(a, zero))(f(a, _))
      acc match {
        case Left(buff) => buffer = Some(buff)
        case Right(result) => sendNext(result); buffer = None
      }
      request(1L)
    }
    override def onComplete() = {
      buffer.foreach { b => sendNext(finish(b)) }
      sendComplete()
    }
  }
