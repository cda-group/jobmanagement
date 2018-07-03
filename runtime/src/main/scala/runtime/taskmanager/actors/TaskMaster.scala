package runtime.taskmanager.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.pattern._
import akka.util.Timeout
import runtime.common._
import runtime.common.Types._
import runtime.taskmanager.utils.{ExecutionEnvironment, TaskManagerConfig}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object TaskMaster {
  def apply(job: ArcJob, slots: Seq[Int], aMaster: AppMasterRef):Props =
    Props(new TaskMaster(job, slots, aMaster))
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
class TaskMaster(job: ArcJob, slots: Seq[Int], appMaster: AppMasterRef)
  extends Actor with ActorLogging with TaskManagerConfig {

  import TaskMaster._
  import akka.io.Tcp._

  // For Akka TCP IO
  import context.system
  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  // Heartbeat variables
  var heartBeatChecker = None: Option[Cancellable]
  var lastJmTs: Long = 0

  // Execution Environment
  val env = new ExecutionEnvironment(job.id)

  // TaskExecutor
  var executors = mutable.IndexedSeq.empty[TaskExecutorRef]

  // TaskReceiver
  var taskReceivers = mutable.HashMap[InetSocketAddress, TaskReceiverRef]()
  var taskReceiversId = 0


  override def preStart(): Unit = {
    env.create() match {
      case Success(_) =>
        log.info("Created job environment: " + env.getJobPath)
      case Failure(e) =>
        log.error("Failed to create job environment with path: " + env.getJobPath)
        // Notify AppMaster
        appMaster ! TaskMasterFailure
        // Shut down
        context stop self
    }

    lastJmTs = System.currentTimeMillis()
    heartBeatChecker = Some(context.system.scheduler.schedule(
      0.milliseconds, taskMasterTimeout.milliseconds,
      self, VerifyHeartbeat))
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
      taskReceivers.get(remote) match {
        case Some(ref) =>
          startExecutors(ref)
        case None =>
          log.error("Was not able to locate ref for remote: " + remote)
      }
      case TasksCompiled=>
      // Binaries are ready to be transferred, open an Akka IO TCP
      // channel and let the AppMaster know how to connect
      val askRef = sender()
      //TODO: fetch host from config
      IO(Tcp) ? Bind(self, new InetSocketAddress("localhost", 0)) onComplete {
        case Success(resp) => resp match {
          case Bound(localAddr) =>
            askRef ! TaskTransferConn(localAddr)
          case CommandFailed(_ :Bind) =>
            askRef ! TaskTransferError
        }
        case Failure(e) =>
          askRef ! TaskTransferError
      }
    case VerifyHeartbeat =>
      val now = System.currentTimeMillis()
      val time = now - lastJmTs
      if (time > taskMasterTimeout) {
        log.info("Did not receive communication from AppMaster: " + appMaster + " within " + taskMasterTimeout + " ms")
        log.info("Releasing slots: " + slots)
        shutdown()
      }
    case TaskMasterHeartBeat =>
      lastJmTs = System.currentTimeMillis()
    case Terminated(ref) =>
      executors = executors.filterNot(_ == ref)
      // TODO: handle scenario where not all executors have been started
      if (executors.isEmpty) {
        log.info("No alive TaskExecutors left, releasing slots")
        shutdown()
      }
  }


  private def startExecutors(ref: TaskReceiverRef): Unit = {
    ref ? TaskUploaded onComplete {
      case Success(resp) => resp match {
        case TaskReady(binId) =>
          // improve names...
          job.job.tasks.foreach {task =>
            // Create 1 executor for each task
            val executor = context.actorOf(TaskExecutor(env.getJobPath+"/" + binId, task, appMaster))
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
    // Cancel ticker
    heartBeatChecker.map(_.cancel())

    // Notify parent and shut down the actor
    context.parent ! ReleaseSlots(slots)
    context.stop(self)
  }
}
