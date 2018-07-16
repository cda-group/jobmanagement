package runtime.appmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.Identifiers


object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class RmRegistration(addr: Address)
  case class UnreachableRm(addr: Address)
  case class RmRemoved(addr: Address)
}

class ClusterListener extends Actor
  with ActorLogging with AppManagerConfig {
  import ClusterListener._

  val appManagerMode = {
    resourcemanager match {
      case "yarn" =>
        log.info("Using YARN as Cluster Manager")
        yarn(context.actorOf(YarnAppManager(), Identifiers.APP_MANAGER))
      case _ =>
        log.info("Using Custom Arc Cluster Manager")
        arc(context.actorOf(ArcAppManager(), Identifiers.APP_MANAGER))
    }
  }

  def receive = appManagerMode

  val cluster = Cluster(context.system)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  /**
    * ClusterListener while in ARC mode.
    */
  def arc(appManager: ActorRef): Receive = {
    case MemberUp(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRegistration(member.address)
    case UnreachableMember(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! UnreachableRm(member.address)
    case MemberRemoved(member, previousStatus)
      if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRemoved(member.address)
    case _ =>
  }

  /**
    * ClusterListener while in YARN mode.
    */
  def yarn(appManager: ActorRef): Receive = {
    case _ =>
  }

}
