package clustermanager.standalone.taskmanager.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.pattern._
import akka.util.Timeout
import clustermanager.common.executor.ExecutionEnvironment
import clustermanager.standalone.taskmanager.isolation.CgroupController
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.Identifiers
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


private[taskmanager] object TaskMaster {
  def apply(container: Container):Props =
    Props(new TaskMaster(container))
  def apply(container: Container, cgController: CgroupController):Props =
    Props(new TaskMaster(container, Some(cgController)))
  case class TaskUploaded(name: String)
  case class TaskReady(id: String)
  case class TaskWriteFailure(name: String)
  case object StartExecution
}

/** A TaskMaster acts as a coordinator for a Container.
  * @param container Container
  * @param cgroupController Controller for the containers Cgroup if cgroups is enabled
  */
private[taskmanager] class TaskMaster(container: Container, cgroupController: Option[CgroupController] = None)
  extends Actor with ActorLogging with TaskManagerConfig {

  import TaskMaster._
  import akka.io.Tcp._
  import context.dispatcher
  // For Akka TCP IO
  import context.system

  // Execution Environment
  private val env = new ExecutionEnvironment(container.jobId)

  // TaskExecutor
  private var executors = IndexedSeq.empty[ActorRef]

  // For futures
  implicit val timeout = Timeout(2 seconds)

  import runtime.protobuf.ProtoConversions.ActorRef._
  private val appmaster: ActorRef = container.appmaster

  // TaskReceiver
  private var taskReceivers = mutable.HashMap[InetSocketAddress, ActorRef]()
  private var taskReceiversId: Long = 0

  private var stateMaster = None: Option[ActorRef]


  override def preStart(): Unit = {
    envSetup()
    notifyAppMaster()
  }

  override def postStop(): Unit = {
    env.clean()
    cgroupController match {
      case Some(controller) =>
        controller.clean()
      case None => // ignore
    }
  }


  def receive = {
    case Connected(remote, local) =>
      val tr = context.actorOf(TaskReceiver(env), "rec"+taskReceiversId)
      taskReceiversId += 1
      taskReceivers.put(remote, tr)
      sender() ! Register(tr)
    case TaskTransferComplete(remote, taskName) =>
      import runtime.protobuf.ProtoConversions.InetAddr._
      taskReceivers.get(remote) match {
        case Some(ref) =>
          if (stateMaster.isDefined)
            ref ? TaskUploaded(taskName) pipeTo self
          else
            log.error("No StateMaster connected to the TaskMaster")
        case None =>
          log.error("Was not able to locate ref for remote: " + remote)
      }
    case TaskWriteFailure(name) =>
      log.error(s"Was not able to write binary $name to file")
      // Handle it
    case TaskReady(name) =>
      initExecutor(stateMaster.get, name)
    case TasksCompiled() =>
      // Binaries are ready to be transferred, open an Akka IO TCP
      // channel and let the AppMaster know how to connect
      val appMaster = sender()
      IO(Tcp) ? Bind(self, new InetSocketAddress(hostname, 0)) map {
        case Bound(inet) =>
          import runtime.protobuf.ProtoConversions.InetAddr._
          appMaster ! TaskTransferConn(inet)
        case CommandFailed(f) =>
          appMaster ! TaskTransferError()
      }
    case Terminated(ref) =>
      executors = executors.filterNot(_ == ref)
      // TODO: handle scenario where not all executors have been started
      if (executors.isEmpty) {
        log.info("No alive TaskExecutors left, releasing slices")

        // Notify AppMaster and StateMaster that this job is being killed..
        appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_KILLED)
        stateMaster.foreach(_ ! TaskMasterStatus(Identifiers.ARC_JOB_KILLED))
        shutdown()
      }
  }

  private def initExecutor(stateMaster: ActorRef, name: String): Unit = {
    container.tasks.find(_.name == name) match {
      case Some(task) =>
        val executor = cgroupController match {
          case Some(controller) =>
            context.actorOf(TaskExecutor(env.getJobPath+"/" + name, task, appmaster, stateMaster, controller),
              Identifiers.TASK_EXECUTOR+"_"+name)
          case None =>
            context.actorOf(TaskExecutor(env.getJobPath+"/" + name, task, appmaster, stateMaster),
              Identifiers.TASK_EXECUTOR+"_"+name)
        }

        executors = executors :+ executor
        // Enable DeathWatch
        context watch executor

        // If we have initialized all tasks, then start execution
        if (container.tasks.lengthCompare(executors.size) == 0) {
          log.info("All executors have been initialized, starting them up!")
          executors.foreach(_ ! StartExecution)
          appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_RUNNING)
        }
      case None =>
        log.error("Was not able to match received task with task in the container")
    }
  }


  /** Sets up the ExecutionEnvironment
    * where binaries are placed and started from.
    */
  private def envSetup(): Unit = {
    env.create() match {
      case Success(_) =>
        log.debug("Created job environment: " + env.getJobPath)
      case Failure(e) =>
        log.error("Failed to create job environment with path: " + env.getJobPath)
        // Notify AppMaster
        appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_FAILED)
        shutdown()
    }
  }

  // Refactor
  private def notifyAppMaster(): Unit = {
    val f = appmaster ? TaskMasterUp(container, self)
    implicit val scheduler = context.system.scheduler
    val notify = retry(
      () ⇒ f,
      3,
      3000.milliseconds
    )

    notify map {
      case StateMasterConn(ref) =>
        stateMaster = Some(ref)
      case x =>
        log.error("Failed to fetch a StateMaster, shutting down!")
        // We were not able to establish communication with the appmaster
        // Release the slices and shutdown
        shutdown()
    }
  }

  private def shutdown(): Unit = {
    // Notify parent and shut down the actor
    context.parent ! ReleaseSlices(container.slices.map(_.index))
    context.stop(self)
  }
}
