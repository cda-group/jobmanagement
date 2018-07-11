package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import runtime.common.messages.{ArcJob, ExecutorMetric}


object StateMaster {
  def apply(implicit appMaster: ActorRef, job: ArcJob): Props =
    Props(new StateMaster(appMaster, job))
}

/**
  * StateMaster receives metrics from running apps that are
  * connected to a specific AppMaster.
  */
class StateMaster(appMaster: ActorRef, job: ArcJob) extends Actor with ActorLogging {

  override def preStart(): Unit =
    context watch appMaster

  def receive = {
    case metric@ExecutorMetric(state, cpu, mem, io) =>
    case Terminated(ref) =>
      // AppMaster has been terminated
      // Handle
      // context stop self
    case _ =>
  }

}
