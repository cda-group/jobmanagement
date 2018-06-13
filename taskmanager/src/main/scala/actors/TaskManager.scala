package actors

import actors.ClusterListener.{RemovedResourceManager, UnreachableResourceManager}
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import common._
import utils.SlotHashMap
import utils.TaskManagerConfig

import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case class TaskSlotUpdate(index:Int, slot: TaskSlot)
}


/**
  * The TaskManager keeps track of the availability of each
  * TaskSlot it provides.
  */
class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  var slotTicker = None: Option[Cancellable]
  var slotsPool = new SlotHashMap[Int, (TaskSlot, ActorRef)](nrOfSlots)
  var initialized = false
  var resourceManager = None: Option[ActorRef]

  import context.dispatcher
  import TaskManager._

  def receive = {
    case TaskManagerInit if !initialized =>
      initialized = true
      resourceManager = Some(sender())

      for (i <- 1 to nrOfSlots) {
        val slot = TaskSlot(i, Utils.testResourceProfile())
        val child = context.actorOf(SlotHandler(i, slot), "slothandler"+i)
        slotsPool.put(i, (slot.copy(),child))
      }
      slotTicker = startUpdateTicker(sender())
    case TaskSlotUpdate(index, slot) => // Update from a slotHandler
      slotsPool.put(index, (slot, sender()))
    case UnreachableResourceManager(manager) =>
      // TODO: Cancel current ticker and set TaskManager as "inactive"
      // and when it connects back to the RM, set to "active"?
    case RemovedResourceManager(manager) =>
      // TODO: Similar to above
    case _ => println("")
  }

  /** Starts ticker to send slot availability periodically to
    * the resource manager
    * @param resourceManager ActorRef to the responsible RM
    * @return Option[Cancellable]
    */
  private def startUpdateTicker(resourceManager: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      slotTick.milliseconds) {
      resourceManager ! SlotUpdate(currentSlots())
    })
  }

  // Look into improving this
  private def currentSlots(): Set[TaskSlot] = {
    slotsPool.map(_._2._1)
      .toSet
  }

}
