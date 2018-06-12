package actors

import akka.actor.{Actor, ActorLogging, Address, Props, RootActorPath}
import common._

import scala.collection.mutable

object SlotManager {
  def apply(): Props = Props(new SlotManager)
}

class SlotManager extends Actor with ActorLogging {
  import ClusterListener._

  val taskManagers = mutable.HashSet[Address]()
  val slots = mutable.HashMap[Address, Set[TaskSlot]]()

  def receive = {
    case TaskManagerRegistration(tm) if !taskManagers.contains(tm) =>
      taskManagers += tm
      val target = context.actorSelection(Utils.taskManagerPath(tm))
      // TODO: add retry logic in case worker is not reachable
      // in order to make sure that the TaskManager is initialized
      target ! TaskManagerInit
    case TaskManagerRemoved(tm) =>
      cleanTaskManager(tm)
    case UnreachableTaskManager(tm) =>
      cleanTaskManager(tm)
    case SlotAvailibility(s) =>
      slots.put(sender().path.address, s)
    case JobRequest(id) =>
      log.info("Got a job request from a driver")
      //TODO: Check if there are available slots for the job
    case _ =>
  }

  private def cleanTaskManager(tm: Address): Unit = {
    taskManagers.remove(tm)
    slots.remove(tm)
  }
}
