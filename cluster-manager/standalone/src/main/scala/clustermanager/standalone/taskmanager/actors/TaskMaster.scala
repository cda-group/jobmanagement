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
  case object VerifyHeartbeat
  case object TaskUploaded
  case class TaskReady(id: String)
  case object TaskWriteFailure
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

  private var stateMaster = None: Option[ActorRef]


  override def preStart(): Unit = {
    val f = appmaster ? TaskMasterUp(container, self)
    implicit val scheduler = context.system.scheduler
    val notify = retry(
      () ⇒ f,
      3,
      3000.milliseconds
    )

    notify onComplete {
      case Success(v) => v match {
        case StateMasterConn(ref) =>
          stateMaster = Some(ref)
        case _ =>
          log.error("Expected StateMasterConn Message, but did not receive.")
      }
      case Failure(e) =>
        log.error(e.toString)
      // We were not able to establish communication with the appmaster
      // Release the slices and shutdown
    }

    env.create() match {
      case Success(_) =>
        log.info("Created job environment: " + env.getJobPath)
      case Failure(e) =>
        log.error("Failed to create job environment with path: " + env.getJobPath)
        // Notify AppMaster
        appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_FAILED)
        // Shut down
        context stop self
    }
  }

  override def postStop(): Unit = {
    env.clean()
  }

  def receive = {
    case Connected(remote, local) =>
      val tr = context.actorOf(TaskReceiver(taskReceiversId.toString, env), "rec"+taskReceiversId)
      taskReceiversId += 1
      taskReceivers.put(remote, tr)
      sender() ! Register(tr)
    case TaskTransferComplete(remote) =>
      import runtime.protobuf.ProtoConversions.InetAddr._
      taskReceivers.get(remote) match {
        case Some(ref) =>
          if (stateMaster.isDefined)
            startExecutors(ref, stateMaster.get)
          else
            log.error("No StateMaster connected to the TaskMaster")
        case None =>
          log.error("Was not able to locate ref for remote: " + remote)
      }
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
        log.info("No alive TaskExecutors left, releasing slots")

        // Notify AppMaster and StateMaster that this job is being killed..
        appmaster ! TaskMasterStatus(Identifiers.ARC_JOB_KILLED)
        stateMaster.foreach(_ ! TaskMasterStatus(Identifiers.ARC_JOB_KILLED))
        shutdown()
      }
  }


  private def startExecutors(taskReceiver: ActorRef, stateMaster: ActorRef): Unit = {
    taskReceiver ? TaskUploaded onComplete {
      case Success(resp) => resp match {
        case TaskReady(binId) =>
          container.tasks.foreach {task =>
            // Create 1 executor for each task
            val executor = context.actorOf(TaskExecutor(env.getJobPath+"/" + binId, task, appmaster, stateMaster),
              UUID.randomUUID().toString)
            executors = executors :+ executor
            // Enable DeathWatch
            context watch executor
          }
        case TaskWriteFailure =>
          log.error("Failed writing to file")
      }
      case Failure(e) =>
        log.error(e.toString)
    }
  }

  private def shutdown(): Unit = {
    // Notify parent and shut down the actor
    context.parent ! ReleaseSlices(container.slices.map(_.index))
    context.stop(self)
  }
}
