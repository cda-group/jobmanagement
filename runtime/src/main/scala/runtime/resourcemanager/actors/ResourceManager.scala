package runtime.resourcemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import runtime.common._
import runtime.common.models.{AllocateFailure, ArcJob, NoSlotsAvailable, Unexpected}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
    case job@ArcJob(_, _, _,  ref) =>
      log.info("Got a job request from an AppMaster")
      val askRef  = sender()
      ref match {
        case Some(_) =>
          slotManager ? SlotRequest(job) onComplete {
            case Success(resp) =>
              askRef ! resp
            case Failure(e) =>
              askRef ! AllocateFailure().withUnexpected(Unexpected())
          }
        case None =>
          log.error("AppMaster Ref was not set in the ArcJob")
          // Notify
      }
    case _ =>
  }
}
