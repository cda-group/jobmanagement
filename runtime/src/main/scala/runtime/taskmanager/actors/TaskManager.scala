package runtime.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props, Terminated}
import akka.cluster.metrics.ClusterMetricsExtension
import akka.pattern._
import akka.util.Timeout
import runtime.common._
import runtime.common.messages.SlotState.{ALLOCATED, FREE}
import runtime.common.messages._
import runtime.taskmanager.actors.TaskManager.{StateMasterError, TMNotInitialized}
import runtime.taskmanager.utils.TaskManagerConfig

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case object TMNotInitialized
  case object StateMasterError
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
  import runtime.common.messages.ProtoConversions.ActorRef._

  var slotTicker = None: Option[Cancellable]
  var taskSlots = mutable.IndexedSeq.empty[TaskSlot]
  var initialized = false
  var resourceManager = None: Option[ActorRef]

  var taskMasters = mutable.IndexedSeq.empty[ActorRef]
  var taskMastersId: Long = 0

  var stateManagers = mutable.IndexedSeq.empty[Address]
  var stateManagerReqs: Int = 0

  import context.dispatcher

  val extension = ClusterMetricsExtension(context.system)


  override def preStart(): Unit = {
   extension.subscribe(self)

    // Static number of fake slots for now
    for (i <- 1 to nrOfSlots) {
      val slot = TaskSlot(i, Utils.slotProfile())
      taskSlots = taskSlots :+ slot
    }
  }

  override def postStop(): Unit = {
    extension.unsubscribe(self)
  }


  def receive = {
    case TaskManagerInit() if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      slotTicker = startUpdateTicker(sender())
    case Allocate(_,_) if !initialized =>
      sender() ! TMNotInitialized
    case Allocate(_,_) if stateManagers.isEmpty =>
      sender() ! AllocateFailure().withUnexpected(Unexpected()) //TODO: Fix
    case Allocate(job, slots) =>
      if (!slotControl(slots)) {
        // we failed allocating the slots
        sender() ! AllocateFailure().withUnexpected(Unexpected()) // Fix this..
      } else {
        val resourceManager = sender()
        val appMaster = job.appMasterRef.get

        val taskMaster = context.actorOf(TaskMaster(job, slots.map(_.index),
          appMaster), Identifiers.TASK_MASTER + taskMastersId)

        taskMasters = taskMasters :+ taskMaster
        taskMastersId += 1

        // Enable DeathWatch
        context watch taskMaster

        // Let the requester know how to access the newly created TaskMaster
        resourceManager ! AllocateSuccess(job, taskMaster)

        // Create a state master that is linked with the AppMaster and TaskMaster
        getStateMaster(appMaster, job) recover {case _ => StateMasterError} pipeTo taskMaster
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

  /** Helper method for the TaskSlot update ticker
    * @return Seq[TaskSlot]
    */
  private def currentSlots(): Seq[TaskSlot] = taskSlots


  /** Does a control check that the slots requested
    * are in fact not occupied
    * @param slots TaskSlots that have been requested
    * @return true if all are free, false if any of them have an ALLOCATED state
    */
  private def slotControl(slots: Seq[TaskSlot]): Boolean = {
    val targetSlots = taskSlots intersect slots
    if (targetSlots.exists(_.state != SlotState.FREE)) {
      false
    } else {
      taskSlots = taskSlots.map { s =>
        if (targetSlots.contains(s))
          s.copy(state = ALLOCATED)
        else
          s
      }
      true
    }
  }

  private def getStateMaster(amRef: ActorRef, job: ArcJob): Future[StateMasterConn] = {
    val smAddr = stateManagers(stateManagerReqs % stateManagers.size)
    val smSelection = context.actorSelection(ActorPaths.stateManager(smAddr))
    implicit val timeout = Timeout(2 seconds)
    smSelection ? StateManagerJob(amRef, job) flatMap {
      case s@StateMasterConn(_) => Future.successful(s)
    } recoverWith {
      case t: akka.pattern.AskTimeoutException => Future.failed(t)
    }
  }

}
