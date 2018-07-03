package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, Props, Terminated}
import runtime.common.Types.AppMasterRef


object StateMaster {
  def apply(appMaster: AppMasterRef): Props =
    Props(new StateMaster(appMaster))
}

/**
  * StateMaster receives metrics from running apps that are
  * connected to a specific AppMaster.
  */
class StateMaster(appMaster: AppMasterRef) extends Actor with ActorLogging {

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
