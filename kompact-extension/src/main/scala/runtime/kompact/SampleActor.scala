package runtime.kompact

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import runtime.kompact.KompactAsk.{AskFailure, AskResponse, AskSuccess}
import runtime.kompact.messages.{Hello, KompactAkkaMsg}


class SampleActor extends Actor with ActorLogging {
  private var kompactRefs = IndexedSeq.empty[KompactRef]

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

      val ma = KompactAkkaMsg().withHello(__v = Hello("hej"))
      ref ! ma

      // Ask example
      val m = KompactAkkaMsg().withHello(Hello("asky"))
      implicit val sys = context.system
      implicit val ec = context.system.dispatcher
      implicit val timeout = Timeout(3.seconds)
      (ref ? m) map {
        case AskSuccess(v) => log.info("succ: " + v)
        case AskFailure => log.error("Ask failure")
      }
    case ExecutorTerminated(ref) =>
      println("exeuctor terminated")
      kompactRefs = kompactRefs.filterNot(_ == ref)
    case Hello(v) =>
      log.info("SampleActor got Hello: " + v)
    case _ =>
  }

}
