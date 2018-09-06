package clustermanager.standalone.taskmanager.actors

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.pattern._
import akka.util.Timeout
import clustermanager.common.executor.ExecutionEnvironment
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.Identifiers
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


private[standalone] object TaskMaster {
  def apply(container: Container):Props =
    Props(new TaskMaster(container))
  final case object VerifyHeartbeat
  final case class TaskUploaded(name: String)
  final case class TaskReady(id: String)
  final case object TaskWriteFailure
  final case object StartExecution
}

/** Actor that manages received Binaries
  *
  * On a Successful ArcJob Allocation, a TaskMaster is created
  * to handle the incoming Tasks from the AppMaster once they have been compiled.
  * A TaskMaster expects heartbeats from the AppMaster, if none are received within
  * the specified timeout, it will consider the job cancelled and instruct the
  * TaskManager to release the slots tied to it
  */
private[standalone] class TaskMaster(container: Container)
  extends Actor with ActorLogging with TaskManagerConfig {

  import TaskMaster._
  import akka.io.Tcp._

  // For Akka TCP IO
  import context.system
  // For futures
  implicit val timeout = Timeout(2 seconds)

  import context.dispatcher

  import runtime.protobuf.ProtoConversions.ActorRef._
  private val appmaster: ActorRef = container.appmaster

  // Container Environment
  // TODO: cgroups...

  // Execution Environment
  private val env = new ExecutionEnvironment(container.jobId)

  // TaskExecutor
  private var executors = mutable.IndexedSeq.empty[ActorRef]

  // TaskReceiver
  private var taskReceivers = mutable.HashMap[InetSocketAddress, ActorRef]()
  private var taskReceiversId = 0

  private var stateMasterConn = None: Option[StateMasterConn]


  override def preStart(): Unit = {
    envSetup()

    // Let the AppMaster know that a container has been allocated for it
    val f = appmaster ? TaskMasterUp(container, self)
    implicit val scheduler = context.system.scheduler
    val notify = retry(
      () ⇒ f,
      3,
      3000.milliseconds
    )

    notify onComplete {
      case Success(v) => v match {
        case conn@StateMasterConn(ref, proxyAddr) =>
          stateMasterConn = Some(conn)
        case _ =>
          log.error("Expected StateMasterConn Message, but did not receive.")
      }
      case Failure(e) =>
        log.error(e.toString)
        shutdown()
      // We were not able to establish communication with the appmaster
      // Release the slices and shutdown
    }

  }

  override def postStop(): Unit = {
    env.clean()
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
          if (stateMasterConn.isDefined)
            ref ? TaskUploaded(taskName) pipeTo self
          else
            log.error("No StateMaster connected to the TaskMaster")
        case None =>
          log.error("Was not able to locate ref for remote: " + remote)
      }
    case TaskReady(name) =>
      initExecutor(stateMasterConn.get, name)
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
        //stateMasterConn.foreach(_.ref ! TaskMasterStatus(Identifiers.ARC_JOB_KILLED))
        shutdown()
      }
  }

  private def initExecutor(stateMasterConn: StateMasterConn, name: String): Unit = {
    container.tasks.find(_.name == name) match {
      case Some(task) =>
        val executor = context.actorOf(TaskExecutor(env, task, appmaster, stateMasterConn),
          Identifiers.TASK_EXECUTOR+"_"+name)
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


  private def envSetup(): Unit = {
    env.create() match {
      case Success(_) =>
        log.info("Created job environment: " + env.getJobPath)
      case Failure(e) =>
        log.error("Failed to create job environment with path: " + env.getJobPath)
        // Notify AppMaster
        appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_FAILED)
        shutdown()
    }
  }

  private def shutdown(): Unit = {
    // Notify parent and shut down the actor
    context.parent ! ReleaseSlices(container.slices.map(_.index))
    context.stop(self)
  }
}
