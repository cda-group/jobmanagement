package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import common._
import utils.SlotHashMap
import utils.TaskManagerConfig

import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case class TaskSlotUpdate(index:Int, slot: TaskSlot)
}

class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  var slotTicker = None: Option[Cancellable]
  var slotsPool = new SlotHashMap[Int, (TaskSlot, ActorRef)](nrOfSlots)
  var initialized = false

  import context.dispatcher
  import TaskManager._

  def receive = {
    case TaskManagerInit if !initialized =>
      initialized = true
      for (i <- 1 to nrOfSlots) {
        val slot = TaskSlot(i, testResourceProfile())
        val child = context.actorOf(SlotHandler(i, slot), "slothandler"+i)
        slotsPool.put(i, (slot.copy(),child))
      }
      slotTicker = startUpdateTicker(sender())
    case TaskSlotUpdate(index, slot) => // Update from a slotHandler
      slotsPool.update(index, (slot, slotsPool(index)._2))
    case _ => println("")
  }

  /** Starts ticker to send slot availibility periodically to
    * the resource manager
    * @param resourceManager ActorRef to the responsible RM
    * @return Option[Cancellable]
    */
  private def startUpdateTicker(resourceManager: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      slotTick.milliseconds,
      resourceManager,
      SlotAvailibility(currentSlots())
    ))
  }

  // Look into improving this
  private def currentSlots(): Set[TaskSlot] =
    slotsPool.map(_._2._1)
      .toSet

  // For development
  private def testResourceProfile(): ResourceProfile =
    ResourceProfile(1.0, 200, 200, 200)

}
