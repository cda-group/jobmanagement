package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import runtime.common.{Identifiers, ProtoConversions}
import runtime.common.models.{StateManagerJob, StateMasterConn}

import scala.collection.mutable


object StateManager {
  def apply(): Props = Props(new StateManager())
}

/**
  * StateManager is a node in the ARCluster that
  * is responsible for creating StateMasters for each
  * ArcJob. StateMasters receive monitoring stats and
  * can act upon them.
  */
class StateManager extends Actor with ActorLogging {
  var stateMasters = mutable.IndexedSeq.empty[ActorRef]
  var stateMasterId: Long = 0

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import ProtoConversions.ActorRef._

  def receive = {
    case StateManagerJob(ref) =>
      val stateMaster = context.actorOf(StateMaster(ref), Identifiers.STATE_MASTER + stateMasterId)
      stateMasters = stateMasters :+ stateMaster
      stateMasterId += 1

      // Enable deathwatch
      context watch stateMaster

      // Respond to TaskManager with Ref to StateMaster
      sender() ! StateMasterConn(stateMaster)
    case Terminated(ref) =>
      stateMasters = stateMasters.filterNot(_ == ref)
    case _ =>
  }

}
