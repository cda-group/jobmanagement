package actors

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import common.{WorkerInit, WorkerState}
import core.Utils
import utils.WorkerConfig

import scala.concurrent.duration._

object Worker {
  def apply(): Props = Props(new Worker())
}

class Worker extends Actor with ActorLogging with WorkerConfig {
  val cluster = Cluster(context.system)
  var stateUpdater = None: Option[Cancellable]

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  import context.dispatcher

  def receive = {
    case WorkerInit =>
      stateUpdater = Some(context.
        system.scheduler.schedule(
        0 milliseconds,
        heartbeat milliseconds,
        sender(),
        workerState()
      ))
    case _ => println("")
  }

  private def workerState(): WorkerState = {
    //TODO: replace with real stats (cpu, mem...)
    WorkerState(Utils.getAvailableCpu(),Utils.getAvailableMemory())
  }

}
