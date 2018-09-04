package runtime.kompact

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import runtime.kompact.KompactAsk.{AskFailure, AskResponse, AskSuccess}
import runtime.kompact.messages.{Hello, KompactAkkaMsg}


class SampleActor extends Actor with ActorLogging {
  private var kompactRefs = IndexedSeq.empty[KompactRef]
  import KompactApi._
  implicit val sys = context.system
  implicit val ec = context.system.dispatcher
  implicit val timeout = Timeout(3.seconds)

  override def preStart(): Unit =
    KompactExtension(context.system).register(self)

  override def postStop(): Unit =
    KompactExtension(context.system).unregister(self)

  def receive = {
    case ExecutorUp(ref) =>
      import scala.concurrent.duration._
      kompactRefs = kompactRefs :+ ref
      // Enable DeathWatch of Executor
      ref kompactWatch self

      val hello = KompactAkkaMsg().withHello(Hello("hej"))
      ref ! hello

      // Ask example


      (ref ? hello) map {
        case AskSuccess(msg) => log.info("success: " + msg.payload)
        case AskFailure => log.error("Ask failure")
      }

      // Pipe examples

      (ref ? hello) pipeKompactTo self

      (ref ? hello) pipeToKompact ref

    case ExecutorTerminated(ref) =>
      println("exeuctor terminated")
      kompactRefs = kompactRefs.filterNot(_ == ref)
    case Hello(v) =>
      log.info("SampleActor got Hello: " + v)
    case KompactAkkaMsg(payload) =>
      println(payload)
    case _ =>
  }

}
