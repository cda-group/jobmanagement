package actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import common.Utils

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class TaskManagerRegistration(addr: Address)
  case class TaskManagerRemoved(addr: Address)
  case class UnreachableTaskManager(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val resourceManager = context.actorOf(ResourceManager(), Utils.RESOURCE_MANAGER)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole(Utils.TASK_MANAGER) =>
      resourceManager ! TaskManagerRegistration(member.address)
    case UnreachableMember(member) if member.hasRole(Utils.TASK_MANAGER) =>
      resourceManager ! UnreachableTaskManager(member.address)
    case MemberRemoved(member, previousStatus) if member.hasRole(Utils.TASK_MANAGER) =>
      resourceManager ! TaskManagerRemoved(member.address)
    case _ =>
  }

}
