package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}


object StateMaster {
  def apply(implicit appMaster: ActorRef): Props =
    Props(new StateMaster(appMaster))
}

/**
  * StateMaster receives metrics from running apps that are
  * connected to a specific AppMaster.
  */
class StateMaster(appMaster: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit =
    context watch appMaster

  def receive = {
    //case NetworkMetric() =>
    //case ProcessMetric() =>
    case Terminated(ref) =>
      // AppMaster has been terminated
      // Handle
      // context stop self
    case _ =>
  }

}
