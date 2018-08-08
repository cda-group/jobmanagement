package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props, Terminated}
import akka.cluster.Cluster
import akka.cluster.metrics.ClusterMetricsExtension
import clustermanager.common.Hardware
import clustermanager.standalone.taskmanager.isolation.LinuxContainerEnvironment.CgroupsException
import clustermanager.standalone.taskmanager.isolation.{Cgroups, LinuxContainerEnvironment}
import clustermanager.standalone.taskmanager.utils.{ContainerUtils, TaskManagerConfig}
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

/** Actor that handles ContainerSlice's
  *
  * The TaskManager keeps track of the availability of each
  * ContainerSlice it provides. After allocating Slices for a container (job),
  * the TaskManager creates a TaskMaster actor to deal with
  * transfer, execution and monitoring of tasks
  */
class TaskManager extends Actor with ActorLogging with TaskManagerConfig {
  import ClusterListener._
  import TaskManager._

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system

  // Slices
  private[this] var sliceTicker = None: Option[Cancellable]
  private[this] var containerSlices = mutable.IndexedSeq.empty[ContainerSlice]

  // ResourceManager
  private[this] var initialized = false
  private[this] var resourceManager = None: Option[ActorRef]

  // TaskMaster
  private[this] var taskMasters = mutable.IndexedSeq.empty[ActorRef]
  private[this] var taskMastersId: Long = 0

  // LCE
  private val containerEnv: Option[LinuxContainerEnvironment] =
    LinuxContainerEnvironment().toOption

  import context.dispatcher

  private val selfAddr = Cluster(context.system).selfAddress

  override def preStart(): Unit = {
    if (isolation.equalsIgnoreCase("cgroups")) {
      containerEnv match {
        case Some(env) =>
          log.info("Using LinuxContainerEnvironment")
          createSlices(env.getCores, env.getMemory / env.getCores)
        case None =>
          log.error("Exiting as LCE failed to initialize")
          System.exit(1) // pretty
      }
    } else {
      val sliceCores = ContainerUtils.getNumberOfContainerCores
      val totalMem = ContainerUtils.getMemoryForContainers
      val memoryPerSlice = totalMem / sliceCores
      createSlices(sliceCores, memoryPerSlice)
    }
  }

  private def createSlices(sliceCores: Int, memoryPerSlice: Long): Unit = {
    for (i <- 1 to sliceCores) {
      val slice = ContainerSlice(i, ResourceProfile(1, memoryPerSlice), host = selfAddr.toString)
      containerSlices = containerSlices :+ slice
    }
    log.info("Slices built: " + containerSlices)
  }

  override def postStop(): Unit = {
    containerEnv match {
      case Some(lce) =>
        lce.shutdown()
      case None => // Ignore
    }
  }


  def receive = {
    case TaskManagerInit() if !initialized =>
      initialized = true
      resourceManager = Some(sender())
      sliceTicker = startUpdateTicker(sender())
    case ContainerAllocation(id, container) =>
      if (sliceControl(container.slices)) {
        occupySlices(container.slices)
        launchTaskmaster(container)
        sender() ! SlicesAllocated(container.slices)
      } else {
        //TODO: notifiy ResourceManager or AppMaster that the job "failed"
        //sender() ! SlicesAllocated(container.slices.map(_.copy(state = ALLOCATED)))
      }
    case ReleaseSlices(sliceIndexes) =>
      releaseSlices(sliceIndexes)
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

  /** Set the Slices to Allocated
    * @param slices ContainerSlice's
    */
  private def occupySlices(slices: Slices): Unit = {
    containerSlices = containerSlices.map { s =>
      if (slices.contains(s))
        s.copy(state = ALLOCATED)
      else
        s
    }
  }

  /** Set the Slices to FREE
    * @param sliceIndexes Indexes of which slices to be freed
    */
  private def releaseSlices(sliceIndexes: Seq[Int]): Unit = {
    containerSlices = containerSlices.map {s =>
      if (sliceIndexes.contains(s.index))
        s.copy(state = SliceState.FREE)
      else
        s
    }
  }

  /** Launches a TaskMaster actor to act as the master of the
    * allocated Container
    * @param container Container
    */
  private def launchTaskmaster(container: Container): Unit = containerEnv match {
    case Some(lce) =>
      lce.createContainerGroup(container.jobId, container)
      val controller = lce.createController(container.jobId)
      val taskmaster = context.actorOf(TaskMaster(container, controller),
        Identifiers.TASK_MASTER+taskMastersId)
      saveTaskMaster(taskmaster)
    case None =>
      val taskmaster = context.actorOf(TaskMaster(container), Identifiers.TASK_MASTER+taskMastersId)
      saveTaskMaster(taskmaster)
  }

  /** Helper for launchTaskmaster method
    * @param taskmaster ActorRef to created Actor
    */
  private def saveTaskMaster(taskmaster: ActorRef): Unit = {
    taskMastersId = taskMastersId + 1
    taskMasters = taskMasters :+ taskmaster
    // Enable DeathWatch
    context watch taskmaster
  }

}
