package runtime.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props, Terminated}
import akka.pattern._
import akka.util.Timeout
import runtime.common._
import runtime.common.models.SlotState.{ALLOCATED, FREE}
import runtime.common.models._
import runtime.taskmanager.actors.TaskManager.TMNotInitialized
import runtime.taskmanager.utils.TaskManagerConfig

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case object TMNotInitialized
}

/** Actor that handles TaskSlots
  *
  * The TaskManager keeps track of the availability of each
  * TaskSlot it provides. After allocating TaskSlot(s) for the AppMaster,
  * the TaskManager creates a TaskMaster actor to deal with
  * transfer, execution and monitoring of tasks
  */
class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  import ClusterListener._

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import ProtoConversions.ActorRef._

  var slotTicker = None: Option[Cancellable]
  var taskSlots = mutable.IndexedSeq.empty[TaskSlot]
  var initialized = false
  var resourceManager = None: Option[ActorRef]

  var taskMasters = mutable.IndexedSeq.empty[ActorRef]
  var taskMastersId: Long = 0

  var stateManagers = mutable.IndexedSeq.empty[Address]
  var stateManagerReqs: Int = 0

  import context.dispatcher

  override def preStart(): Unit = {
    // Static number of fake slots for now
    for (i <- 1 to nrOfSlots) {
      val slot = TaskSlot(i, Utils.slotProfile())
      taskSlots = taskSlots :+ slot
    }
  }


  def receive = {
    case models.TaskManagerInit if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      slotTicker = startUpdateTicker(sender())
    case Allocate(_,_) if !initialized =>
      sender() ! TMNotInitialized
    case Allocate(_,_) if stateManagers.isEmpty =>
      // improve
      sender() ! AllocateFailure(Unexpected().toByteString)
    case Allocate(job, slots) => // TODO: Refactor
      val targetSlots = taskSlots intersect slots
      if (targetSlots.exists(_.state != SlotState.FREE)) {
        // One of the slots were not free.
        // notify requester that the allocation failed
        sender() ! AllocateFailure(Unexpected().toByteString)
      } else {

        taskSlots = taskSlots.map {s =>
          if (targetSlots.contains(s))
            s.copy(state = ALLOCATED)
          else
            s
        }

        if (job.ref.isEmpty) {
          // ActorRef to AppMaster was not set, something went wrong
          // This should not happen
          // TODO: Handle
        } else {
          // Set reference to actual sender to avoid problems
          // when temporary actors are created...
          val askRef = sender()

          // Check if there are any available StateManagers
          // If there are, then send a StateManagerJob and
          // Collect the StateMaster ref, and give it to the TaskMaster

          // If there are no StateManagers, then perhaps create an
          // StateMaster actor remotely on the driver "instead"
          getStateMaster(job.ref.get) map {
            case Some(stateMaster) =>
              val tm = context.actorOf(TaskMaster(job, slots.map(_.index),
                job.ref.get), Identifiers.TASK_MASTER+taskMastersId)

              taskMasters = taskMasters :+ tm
              taskMastersId += 1

              // Enable DeathWatch
              context watch tm

              // Let AppMaster know about the allocation and how to access the TaskMaster
              askRef ! AllocateSuccess(job, tm)
            case None =>
            // Could not retreive a StateMaster
            // Either we can create a StateMaster actor remotely on the driver
            // or we fail the Allocation..
              sender() ! AllocateFailure(Unexpected().toByteString) // Fix...
          }

        }
      }
    case ReleaseSlots(slots) =>
      taskSlots = taskSlots.map {s =>
        if (slots.contains(s.index))
          s.copy(state = FREE)
        else
          s
      }
    case Terminated(ref) =>
      taskMasters = taskMasters.filterNot(_ == ref)
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
    case StateManagerUp(manager) =>
      stateManagers = stateManagers :+ manager
    case UnreachableStateManager(manager) =>
      // TODO: Handle by either removing or try to wait for a reconnection
    case RemovedStateManager(manager) =>
      stateManagers = stateManagers.filterNot(_ == manager)
  }

  /** Starts ticker to send slot availability periodically to
    * the resource manager
    * @param rm ActorRef to the responsible RM
    * @return Option[Cancellable]
    */
  private def startUpdateTicker(rm: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      slotTick.milliseconds) {
      rm ! SlotUpdate(currentSlots())
    })
  }

  private def currentSlots(): Seq[TaskSlot] = taskSlots

  private def getStateMaster(amRef: ActorRef): Future[Option[ActorRef]] = {
    val smAddr = stateManagers(stateManagerReqs % stateManagers.size)
    val smSelection = context.actorSelection(ActorPaths.stateManager(smAddr))
    implicit val timeout = Timeout(3 seconds)
    smSelection ? StateManagerJob(amRef) flatMap {
      case StateMasterConn(stateMaster) =>
        Future.successful(Some(stateMaster))
    }
  }


}
