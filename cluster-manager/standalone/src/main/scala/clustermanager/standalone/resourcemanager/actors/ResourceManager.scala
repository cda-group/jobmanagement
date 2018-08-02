package clustermanager.standalone.resourcemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import clustermanager.standalone.resourcemanager.utils.RmConfig
import runtime.common.Identifiers
import runtime.protobuf.messages.ArcJob

import scala.collection.mutable
import scala.concurrent.duration._

private[resourcemanager] object ResourceManager {
  def apply(): Props = Props(new ResourceManager)
  case class SlotRequest(job: ArcJob)
  case class ResourceRequest(job: ArcJob)
}

/**
  * The ResourceManager is responsible for handling the
  * computing resources in the Standalone Cluster
  * 1. Receives Jobs from AppMasters
  * 2. Utilises a SliceManager in order to keep track of free ContainerSlices
  *    in the Cluster
  */
private[resourcemanager] class ResourceManager extends Actor with ActorLogging with RmConfig {
  import ClusterListener._
  import ResourceManager._

  private[this] val scheduler = {
    try {
      val clazz = Class.forName(schedulerFQCN).asInstanceOf[Class[Scheduler]]
      context.actorOf(Props.apply(clazz), Identifiers.SCHEDULER)
    } catch {
      case err: Exception =>
        log.error(err.toString)
        log.info("Using the default scheduler: RoundRobinScheduler")
        context.actorOf(Props(new RoundRobinScheduler), Identifiers.SCHEDULER)
    }
  }

  def receive = {
    case tmr@TaskManagerRegistration(_) =>
      scheduler forward tmr
    case tmr@TaskManagerRemoved(_) =>
      scheduler forward tmr
    case utm@UnreachableTaskManager(_) =>
      scheduler forward utm
    case job@ArcJob(_, _, _, _, _ ,_) =>
      scheduler forward ResourceRequest(job)
    case _ =>
  }
}
