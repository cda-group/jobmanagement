package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props, Terminated}
import akka.cluster.metrics.ClusterMetricsExtension
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.Identifiers
import runtime.protobuf.messages.SliceState.{ALLOCATED, FREE}
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.duration._

object TaskManager {
  def apply(): Props = Props(new TaskManager())
  case object TMNotInitialized
  case object StateMasterError
  type Slices = Seq[ContainerSlice]
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

  private[this] var sliceTicker = None: Option[Cancellable]
  private[this] var containerSlices = mutable.IndexedSeq.empty[ContainerSlice]
  private[this] var initialized = false
  private[this] var resourceManager = None: Option[ActorRef]
  private[this] var taskMasters = mutable.IndexedSeq.empty[ActorRef]
  private[this] var taskMastersId: Long = 0

  import context.dispatcher
  private val metrics = ClusterMetricsExtension(context.system)

  override def preStart(): Unit = {
   metrics.subscribe(self)

    // Static number of fake slices for now
    for (i <- 1 to nrOfSlots) {
      val slice = ContainerSlice(i, ResourceProfile(1, 2000))
      containerSlices = containerSlices :+ slice
    }
  }

  override def postStop(): Unit = {
    metrics.unsubscribe(self)
  }


  def receive = {
    case TaskManagerInit() if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      sliceTicker = startUpdateTicker(sender())
    case ContainerAllocation(id, container) =>
      if (sliceControl(container.slices)) {
        // Set slices to Allocated
        occupySlices(container.slices)
        val taskmaster = context.actorOf(TaskMaster(container), Identifiers.TASK_MASTER+taskMastersId)
        taskMastersId = taskMastersId + 1
        taskMasters = taskMasters :+ taskmaster
        // Enable DeathWatch
        context watch taskmaster
      } else {
        //TODO: notifiy ResourceManager or AppMaster that the job "failed"
        sender() !  "failed"
      }
    case Terminated(ref) =>
      taskMasters = taskMasters.filterNot(_ == ref)
    case UnreachableResourceManager(manager) =>
      resourceManager = None
      sliceTicker.map(_.cancel())
    // and wait for a ResourceManager to connect back
    case RemovedResourceManager(manager) =>
      resourceManager = None
      sliceTicker.map(_.cancel())
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
      rm ! SliceUpdate(currentSlices())
    })
  }

  /** Helper method for the slices update ticker
    * @return Slices
    */
  private def currentSlices(): Slices = containerSlices


  /** Does a control check that the slices requested
    * are in fact not occupied
    * @param slices ContainerSlices that have been requested
    * @return true if all are free, false if any of them have an ALLOCATED state
    */
  private def sliceControl(slices: Slices): Boolean = {
    (containerSlices intersect slices).
      forall(_.state == SliceState.FREE)
  }

  private def occupySlices(slices: Slices): Unit = {
    containerSlices = containerSlices.map { s =>
      if (slices.contains(s))
        s.copy(state = ALLOCATED)
      else
        s
    }
  }

}
