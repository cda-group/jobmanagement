package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}


object Driver {
  def apply(): Props = Props(new Driver)
}

class Driver extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(
        "Member is Removed: {} after {}",
        member.address, previousStatus)
    case _ =>
  }

  def register(member: Member): Unit = {
   if (member.hasRole("resourcemanager"))  {
   }
  }
}
