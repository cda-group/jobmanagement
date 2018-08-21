package runtime.kompact

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.util.Timeout
import runtime.kompact.messages.KompactAkkaMsg.Msg
import runtime.kompact.messages.KompactAkkaMsg

private[kompact] object KompactAsk {
  sealed trait AskResponse
  case object AskFailure extends AskResponse
  case class AskSuccess(msg: Msg) extends AskResponse
  case object AskExpired
  case object AskTickerInit
  def apply(t: Timeout): Props = Props(new KompactAsk(t))
}

/** Temporary actor that is utilised in a Kompact Ask request.
  * 1. AskTickerInit is called and a timer is started.
  * 2. If a message is received within @t duration,
  *    AskSuccess is sent back, otherwise AskFailure.
  * @param t Akka Timeout
  */
private[kompact] class KompactAsk(t: Timeout) extends Actor with ActorLogging {
  import KompactAsk._
  private var timer: Option[Cancellable] = None
  private var asker: Option[ActorRef] = None

  implicit val ec = context.system.dispatcher

  def receive = {
    case AskExpired =>
      asker.get ! AskFailure
      shutdown()
    case KompactAkkaMsg(_, msg) =>
      asker.get ! AskSuccess(msg)
      shutdown()
    case AskTickerInit =>
      asker = Some(sender())
      timer = Some(context.system.scheduler.scheduleOnce(t.duration, self, AskExpired))
  }

  private def shutdown(): Unit = {
    timer.foreach(_.cancel())
    context stop self
  }
}
