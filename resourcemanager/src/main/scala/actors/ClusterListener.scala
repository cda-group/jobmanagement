package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
  case class WorkerRegistration(ref: ActorRef)
  case class WorkerRemoved(ref: ActorRef)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val jobMaster = context.actorOf(JobMaster(), "jobmaster")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      if (member.hasRole("worker")) {
        jobMaster ! WorkerRegistration(sender())
      }
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      jobMaster ! WorkerRemoved(sender())
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _ =>
  }

}
