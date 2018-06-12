package actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}


object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class RmRegistration(addr: Address)
  case class UnreachableRm(addr: Address)
  case class RmRemoved(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val driver = context.actorOf(Driver(), "handler")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole("resourcemanager") =>
      driver ! RmRegistration(member.address)
    case UnreachableMember(member) if member.hasRole("resourcemanager") =>
      driver ! UnreachableRm(member.address)
    case MemberRemoved(member, previousStatus)
      if member.hasRole("resourcemanager") =>
      driver ! RmRemoved(member.address)
    case _ =>
  }

}
