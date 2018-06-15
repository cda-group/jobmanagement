package actors

import actors.ClusterListener.{RemovedResourceManager, UnreachableResourceManager}
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import common._
import utils.TaskManagerConfig

import scala.collection.mutable
import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
}

/**
  * The TaskManager keeps track of the availability of each
  * TaskSlot it provides. After allocating TaskSlot(s) for JobManager,
  * the TaskManager creates a BinaryManager actor to deal with
  * transfer, execution and monitoring of binaries
  */
class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  var slotTicker = None: Option[Cancellable]
  var taskSlots = mutable.IndexedSeq.empty[TaskSlot]
  var initialized = false
  var resourceManager = None: Option[ActorRef]

  var binaryManagers = mutable.IndexedSeq.empty[ActorRef]
  var binaryManagerId: Long = 0

  import context.dispatcher

  def receive = {
    case TaskManagerInit if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      // Static number of slots for now
      for (i <- 1 to nrOfSlots) {
        val slot = TaskSlot(i, Utils.testResourceProfile())
        taskSlots = taskSlots :+ slot
      }
      slotTicker = startUpdateTicker(sender())
    case Allocate(job, slots) =>
      val targetSlots = taskSlots intersect slots
      if (targetSlots.exists(_.state != Free)) {
        // One of the slots were not free.
        // notify requester that the allocation failed
        sender() ! AllocateFailure(Allocated) // TODO: improve
      } else {
        val updated = targetSlots.map(_.newState(s = Allocated))
        taskSlots = updated union taskSlots
        //  Create BinaryManager
        val bm = context.actorOf(BinaryManager(updated, sender()), Identifiers.BINARY_MANAGER+binaryManagerId)
        binaryManagers = binaryManagers :+ bm
        binaryManagerId += 1

        // Enable DeathWatch
        context watch bm

        // Let JobManager know about the allocation and how to access the BinaryManager
        sender() ! AllocateSuccess(job, bm)
      }
    case ReleaseSlots(slots) =>
      taskSlots = taskSlots.map {s =>
        if (slots.contains(s))
          s.newState(s = Free)
        else
          s
      }
    case Terminated(ref) =>
      binaryManagers = binaryManagers.filterNot(_ == ref)
    case UnreachableResourceManager(manager) =>
    // TODO: Cancel current ticker and set TaskManager as "inactive"
    // and when it connects back to the RM, set to "active"?
    case RemovedResourceManager(manager) =>
    // TODO: Similar to above
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

  private def currentSlots(): Seq[TaskSlot] = taskSlots


}
