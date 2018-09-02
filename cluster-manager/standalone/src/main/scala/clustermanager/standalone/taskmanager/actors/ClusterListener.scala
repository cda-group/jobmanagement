package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import runtime.common.Identifiers

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  // ResourceManager
  final case class UnreachableResourceManager(addr: Address)
  final case class RemovedResourceManager(addr: Address)
  final case class ResourceManagerUp(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {
  import ClusterListener._

  private val cluster = Cluster(context.system)
  private val taskManager = context.actorOf(TaskManager(), Identifiers.TASK_MANAGER)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  def receive = {
    // ResourceManager
    case MemberUp(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! ResourceManagerUp
    case UnreachableMember(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! UnreachableResourceManager(member.address)
    case MemberRemoved(member, previousStatus) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! RemovedResourceManager(member.address)
  }

}
