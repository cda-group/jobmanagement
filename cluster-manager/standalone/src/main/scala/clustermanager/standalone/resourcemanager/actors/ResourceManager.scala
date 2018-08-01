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
  * 2. Utilises a SlotManager in order to keep track of free slots
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

  // For futures
  private implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case tmr@TaskManagerRegistration(_) =>
      scheduler forward tmr
    case tmr@TaskManagerRemoved(_) =>
      scheduler forward tmr
    case utm@UnreachableTaskManager(_) =>
      scheduler forward utm
    case job@ArcJob(_, _, _, _, _ ,_) =>
      log.info("Got a job request from an AppMaster")
      scheduler forward ResourceRequest(job)
      //slotManager ? SlotRequest(job) pipeTo sender()
    case _ =>
  }
}
