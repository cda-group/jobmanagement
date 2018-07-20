package clustermanager.standalone.resourcemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import runtime.common.Identifiers
import runtime.protobuf.messages.ArcJob

import scala.collection.mutable
import scala.concurrent.duration._

object ResourceManager {
  def apply(): Props = Props(new ResourceManager)
  case class SlotRequest(job: ArcJob)
}

/**
  * The ResourceManager is responsible for handling the
  * computing resources in the Arc Cluster.
  * 1. Receives Jobs from AppMasters
  * 2. Utilises a SlotManager in order to keep track of free slots
  */
class ResourceManager extends Actor with ActorLogging {

  import ClusterListener._
  import ResourceManager._

  val activeAppMasters = mutable.HashSet[ActorRef]()
  val slotManager = context.actorOf(SlotManager(), Identifiers.SLOT_MANAGER)

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case tmr@TaskManagerRegistration(_) =>
      slotManager forward tmr
    case tmr@TaskManagerRemoved(_) =>
      slotManager forward tmr
    case utm@UnreachableTaskManager(_) =>
      slotManager forward utm
    case job@ArcJob(_, _, _, _, _) =>
      log.info("Got a job request from an AppMaster")
      slotManager ? SlotRequest(job) pipeTo sender()
    case _ =>
  }
}