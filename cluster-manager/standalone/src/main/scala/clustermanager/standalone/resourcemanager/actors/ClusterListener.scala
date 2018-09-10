package clustermanager.standalone.resourcemanager.actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import runtime.common.Identifiers

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  final case class TaskManagerRegistration(addr: Address)
  final case class TaskManagerRemoved(addr: Address)
  final case class UnreachableTaskManager(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  private val cluster = Cluster(context.system)
  private val resourceManager = context.actorOf(ResourceManager(), Identifiers.RESOURCE_MANAGER)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole(Identifiers.TASK_MANAGER) =>
      resourceManager ! TaskManagerRegistration(member.address)
    case UnreachableMember(member) if member.hasRole(Identifiers.TASK_MANAGER) =>
      resourceManager ! UnreachableTaskManager(member.address)
    case MemberRemoved(member, previousStatus) if member.hasRole(Identifiers.TASK_MANAGER) =>
      resourceManager ! TaskManagerRemoved(member.address)
    case _ =>
  }

}
