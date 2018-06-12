package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}

object ClusterListener {
  def apply(): Props = Props(new ClusterListener)
}

class ClusterListener extends Actor with ActorLogging {

  import ClusterListener._

  val cluster = Cluster(context.system)
  val taskManager = context.actorOf(TaskManager(), "taskmanager")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
    case UnreachableMember(member)  =>
    case MemberRemoved(member, previousStatus) =>
    case _ =>
  }

}
