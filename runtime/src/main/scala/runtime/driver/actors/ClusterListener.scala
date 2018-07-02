package runtime.driver.actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import runtime.common.Identifiers


object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class RmRegistration(addr: Address)
  case class UnreachableRm(addr: Address)
  case class RmRemoved(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val appManager = context.actorOf(AppManager(), Identifiers.APP_MANAGER)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRegistration(member.address)
    case UnreachableMember(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! UnreachableRm(member.address)
    case MemberRemoved(member, previousStatus)
      if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      appManager ! RmRemoved(member.address)
    case _ =>
  }

}
