package alexsmirnov.pbconsole.serial

import org.scalatest.FlatSpec
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.time._
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.reactivestreams.Subscriber
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.reactivestreams.Subscription
import org.mockito.ArgumentCaptor

class PortInteractive extends FlatSpec with GivenWhenThen with Eventually  with MockitoSugar {
  implicit override val patienceConfig =
  PatienceConfig(timeout = Span(60, Seconds), interval = Span(1, Seconds))
  "USB Serial port" should "connect and disconnect" in {
    Given("Printer disconnected")
    val port = Port("/dev/tty.usbmodem.*".r)
    @volatile
    var lastEvent: Option[Port.StateEvent] = None
    port.addStateListener{ ev => lastEvent = Some(ev)}
    val consumer = mock[Subscriber[Byte]]
    val producerSubscription = mock[Subscription]
    port.subscribe(consumer)
    port.onSubscribe(producerSubscription)
    port.run()
    val argSub = ArgumentCaptor.forClass(classOf[Subscription])
    verify(consumer).onSubscribe(argSub.capture)
    val consumerSubscription = argSub.getValue
    consumerSubscription.request(100L)
    When("Printer connected")
    // wait to connect
    println("Connect printer")
    Then("Fire connected event")
    eventually{ assert((lastEvent.collect{case Port.Connected(_,baud) => baud}).isDefined) }
    And("Request some output")
    verify(producerSubscription,atLeastOnce).request(anyLong)
    verifyNoMoreInteractions(producerSubscription)
    And("Produce received data")
    verify(consumer,atLeastOnce).onNext(anyByte)
    verifyNoMoreInteractions(consumer)
    When("Printer disconnected")
    clearInvocations(consumer)
    clearInvocations(producerSubscription)
    lastEvent = None
    consumerSubscription.request(Long.MaxValue)
    println("Disonnect printer")
    Then("Fire disconnected event")
    eventually{ assert(lastEvent === Some(Port.Disconnected)) }
    And("Send cancel to producer")
    verify(producerSubscription).cancel()
    And("Send complete to receiver")
    verify(consumer).onComplete()
    When("Printer connected again")
    // wait to connect
    clearInvocations(consumer)
    clearInvocations(producerSubscription)
    lastEvent = None
    println("Connect printer")
    Then("Fire connected event")
    eventually{ assert((lastEvent.collect{case Port.Connected(_,baud) => baud}).isDefined) }
    consumerSubscription.request(100L)
    And("Request some output")
    verify(producerSubscription,atLeastOnce).request(anyLong)
    verifyNoMoreInteractions(producerSubscription)
    And("Produce received data")
    verify(consumer,atLeastOnce).onNext(anyByte)
  }
}