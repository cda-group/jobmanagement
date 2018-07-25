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

  case class StateManagerRegistration(addr: Address)
  case class StateManagerRemoved(addr: Address)
  case class StateManagerUnreachable(addr: Address)
}

class ClusterListener extends Actor
  with ActorLogging with AppManagerConfig {
  import ClusterListener._

  val appManagerMode: Receive = {
    resourcemanager match {
      case "yarn" =>
        log.info("Using YARN as Cluster Manager")
        yarn(context.actorOf(YarnAppManager(), Identifiers.APP_MANAGER))
      case _ =>
        log.info("Using Standalone Cluster Manager")
        standalone(context.actorOf(StandaloneAppManager(), Identifiers.APP_MANAGER))
    }
  }

  def receive = appManagerMode

  val cluster = Cluster(context.system)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  /**
    * ClusterListener while in Standalone mode.
    */
  def standalone(appManager: ActorRef): Receive = {
    case MemberUp(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRegistration(member.address)
    case UnreachableMember(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! UnreachableRm(member.address)
    case MemberRemoved(member, previousStatus)
      if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRemoved(member.address)
    case MemberUp(m) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerRegistration(m.address)
    case MemberRemoved(m, status) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerRemoved(m.address)
    case UnreachableMember(m) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerUnreachable(m.address)
    case _ =>
  }

  /**
    * ClusterListener while in YARN mode.
    */
  def yarn(appManager: ActorRef): Receive = {
    case MemberUp(m) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerRegistration(m.address)
    case MemberRemoved(m, status) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerRemoved(m.address)
    case UnreachableMember(m) if m.hasRole(Identifiers.STATE_MANAGER) =>
      appManager ! StateManagerUnreachable(m.address)
  }

}
