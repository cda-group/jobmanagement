package runtime.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import runtime.common._
import runtime.taskmanager.actors.TaskManager.TMNotInitialized
import runtime.taskmanager.utils.TaskManagerConfig

import scala.collection.mutable
import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case object TMNotInitialized
}

/** Actor that handles TaskSlots
  *
  * The TaskManager keeps track of the availability of each
  * TaskSlot it provides. After allocating TaskSlot(s) for the AppMaster,
  * the TaskManager creates a BinaryManager actor to deal with
  * transfer, execution and monitoring of binaries
  */
class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  import ClusterListener._
  import runtime.common.Types._

  var slotTicker = None: Option[Cancellable]
  var taskSlots = mutable.IndexedSeq.empty[TaskSlot]
  var initialized = false
  var resourceManager = None: Option[ResourceManagerRef]

  var binaryManagers = mutable.IndexedSeq.empty[BinaryManagerRef]
  var binaryManagerId: Long = 0

  import context.dispatcher

  override def preStart(): Unit = {
    // Static number of fake slots for now
    for (i <- 1 to nrOfSlots) {
      val slot = TaskSlot(i, Utils.slotProfile())
      taskSlots = taskSlots :+ slot
    }
  }


  def receive = {
    case TaskManagerInit if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      slotTicker = startUpdateTicker(sender())
    case Allocate(_,_) if !initialized =>
      sender() ! TMNotInitialized
    case Allocate(job, slots) =>
      val targetSlots = taskSlots intersect slots
      if (targetSlots.exists(_.state != Free)) {
        // One of the slots were not free.
        // notify requester that the allocation failed
        sender() ! AllocateFailure(UnexpectedError)
      } else {
        taskSlots = taskSlots.map {s =>
          if (targetSlots.contains(s))
            s.newState(s = Allocated)
          else
            s
        }
        //  Create BinaryManager
        if (job.masterRef.isEmpty) {
          // ActorRef to AppMaster was not set, something went wrong
          // This should not happen
          // TODO: Handle
        } else {
          val bm = context.actorOf(BinaryManager(job, slots.map(_.index),
            job.masterRef.get), Identifiers.BINARY_MANAGER+binaryManagerId)

          binaryManagers = binaryManagers :+ bm
          binaryManagerId += 1

          // Enable DeathWatch
          context watch bm

          // Let AppMaster know about the allocation and how to access the BinaryManager
          sender() ! AllocateSuccess(job, bm)
        }
      }
    case ReleaseSlots(slots) =>
      taskSlots = taskSlots.map {s =>
        if (slots.contains(s.index))
          s.newState(s = Free)
        else
          s
      }
    case Terminated(ref) =>
      binaryManagers = binaryManagers.filterNot(_ == ref)
    case UnreachableResourceManager(manager) =>
      resourceManager = None
      slotTicker.map(_.cancel())
    // and wait for a ResourceManager to connect back
    case RemovedResourceManager(manager) =>
      resourceManager = None
      slotTicker.map(_.cancel())
    case ResourceManagerUp(manager) =>
    // RM is up.
    // This is not important at this current stage.
  }

  /** Starts ticker to send slot availability periodically to
    * the resource manager
    * @param rm ActorRef to the responsible RM
    * @return Option[Cancellable]
    */
  private def startUpdateTicker(rm: ResourceManagerRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      slotTick.milliseconds) {
      rm ! SlotUpdate(currentSlots())
    })
  }

  private def currentSlots(): Seq[TaskSlot] = taskSlots


}
