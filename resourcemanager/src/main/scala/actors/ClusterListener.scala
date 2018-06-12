package actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class WorkerRegistration(addr: Address)
  case class WorkerRemoved(addr: Address)
  case class UnreachableWorker(addr: Address)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val jobMaster = context.actorOf(JobMaster(), "jobmaster")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) if member.hasRole("worker") =>
      jobMaster ! WorkerRegistration(member.address)
    case UnreachableMember(member) if member.hasRole("worker") =>
      jobMaster ! UnreachableWorker(member.address)
    case MemberRemoved(member, previousStatus) =>
      jobMaster ! WorkerRemoved(member.address)
    case _ =>
  }

}
