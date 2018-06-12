package actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class TaskManagerRegistration(addr: Address)
  case class TaskManagerRemoved(addr: Address)
  case class UnreachableTaskManager(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val slotManager = context.actorOf(SlotManager(), "slotmanager")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole("taskmanager") =>
      slotManager ! TaskManagerRegistration(member.address)
    case UnreachableMember(member) if member.hasRole("taskmanager") =>
      slotManager ! UnreachableTaskManager(member.address)
    case MemberRemoved(member, previousStatus) =>
      slotManager ! TaskManagerRemoved(member.address)
    case _ =>
  }

}
