package actors

import actors.ClusterListener.{TaskManagerRegistration, TaskManagerRemoved, UnreachableTaskManager}
import actors.ResourceManager.SlotRequest
import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props}
import common.{ArcJob, Utils}

import scala.collection.mutable

object ResourceManager {
  def apply(): Props = Props(new ResourceManager)
  case class SlotRequest(job: ArcJob, driver: ActorRef)
}

/**
  *
  */
class ResourceManager extends Actor with ActorLogging {

  // Rename to JobManagers?
  val drivers = mutable.HashSet[Address]()

  val slotManager = context.actorOf(SlotManager(), Utils.SLOT_MANAGER)

  def receive = {
    case tmr@TaskManagerRegistration(_) =>
      slotManager forward tmr
    case tmr@TaskManagerRemoved(_) =>
      slotManager forward tmr
    case utm@UnreachableTaskManager(_) =>
      slotManager forward utm
    case job@ArcJob(_, _) =>
      log.info("Got a job request from a driver")
      // Check available slots through the slotManager
      // If allocated send success back, else failure
      // On success, add driver/jobmanager to set of "active",
      // Send a Message back with the Address/ActorRef to the TaskManager
      // And actorref back to the ResourceManager for heartbeats..
      slotManager ! SlotRequest(job, sender())
    case _ =>
  }
}
