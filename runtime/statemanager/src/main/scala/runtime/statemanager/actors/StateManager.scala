package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.cluster.metrics.ClusterMetricsExtension
import runtime.common.Identifiers
import runtime.protobuf.messages.{StateManagerJob, StateMasterConn}

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
  import runtime.protobuf.ProtoConversions.ActorRef._

  val metrics = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = {
    metrics.subscribe(self)
  }

  override def postStop(): Unit = {
    metrics.unsubscribe(self)
  }

  def receive = {
    case StateManagerJob(appMaster, job) =>
      val stateMaster = context.actorOf(StateMaster(appMaster, job)
        , Identifiers.STATE_MASTER + stateMasterId)
      stateMasters = stateMasters :+ stateMaster
      stateMasterId += 1

      // Enable deathwatch
      context watch stateMaster

      // Respond with Ref to StateMaster
      sender() ! StateMasterConn(stateMaster)
    case Terminated(ref) =>
      stateMasters = stateMasters.filterNot(_ == ref)
    case _ =>
  }

}
