package runtime.taskmanager.actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import runtime.common.Identifiers
import runtime.common.Types.ResourceManagerAddr

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class UnreachableResourceManager(addr: ResourceManagerAddr)
  case class RemovedResourceManager(addr: ResourceManagerAddr)
  case class ResourceManagerUp(addr: ResourceManagerAddr)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val taskManager = context.actorOf(TaskManager(), Identifiers.TASK_MANAGER)

  override def preStart(): Unit =
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[MemberRemoved])
  override def postStop(): Unit =
    cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! ResourceManagerUp
    case UnreachableMember(member) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! UnreachableResourceManager(member.address)
    case MemberRemoved(member, previousStatus) if member.hasRole(Identifiers.RESOURCE_MANAGER) =>
      taskManager ! RemovedResourceManager(member.address)
    case _ =>
  }

}
