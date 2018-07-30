package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props, Terminated}
import akka.cluster.metrics.ClusterMetricsExtension
import akka.pattern._
import akka.util.Timeout
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages.SlotState.{ALLOCATED, FREE}
import runtime.protobuf.messages._

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
  import TaskManager._

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  private[this] var slotTicker = None: Option[Cancellable]
  private[this] var taskSlots = mutable.IndexedSeq.empty[TaskSlot]
  private[this] var initialized = false
  private[this] var resourceManager = None: Option[ActorRef]
  private[this] var taskMasters = mutable.IndexedSeq.empty[ActorRef]
  private[this] var taskMastersId: Long = 0

  import context.dispatcher
  private val metrics = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = {
   metrics.subscribe(self)

    // Static number of fake slots for now
    for (i <- 1 to nrOfSlots) {
      val slot = TaskSlot(i, ArcProfile(1.0, 2000))
      taskSlots = taskSlots :+ slot
    }
  }

  override def postStop(): Unit = {
    metrics.unsubscribe(self)
  }


  def receive = {
    case TaskManagerInit() if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      slotTicker = startUpdateTicker(sender())
    case Allocate(_,_) if !initialized =>
      sender() ! TMNotInitialized
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
}
