package taskmanager.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import akka.io.{IO, Tcp}
import akka.pattern._
import akka.util.Timeout
import common.{BinaryTransferComplete, _}
import taskmanager.utils.{ExecutionEnvironment, TaskManagerConfig}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object BinaryManager {
  def apply(job: ArcJob, slots: Seq[Int], jm: ActorRef):Props =
    Props(new BinaryManager(job, slots, jm))
  case object VerifyHeartbeat
  case object BinaryUploaded
  case class BinaryReady(id: Int)
  case object BinaryWriteFailure
}

/** Actor that manages received Binaries
  *
  * On a Successful ArcJob Allocation, a BinaryManager is created
  * to handle the incoming binaries from the JobManager once they have been compiled.
  * A BinaryManager expects heartbeats from the JobManager, if none are received within
  * the specified timeout, it will consider the job cancelled and instruct the
  * TaskManager to release the slots tied to it
  */
class BinaryManager(job: ArcJob, slots: Seq[Int], jm: ActorRef)
  extends Actor with ActorLogging with TaskManagerConfig {

  import BinaryManager._
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
  val env = new ExecutionEnvironment(job)

  // BinaryExecutor
  var executors = mutable.IndexedSeq.empty[ActorRef]

  // BinaryReceiver
  var binaryReceivers = mutable.HashMap[InetSocketAddress, ActorRef]()
  var binaryReceiversId = 0


  override def preStart(): Unit = {
    env.create() match {
      case Success(_) =>
        log.info("Created job environment: " + env.getJobPath)
      case Failure(e) =>
        log.error("Failed to create job environment with path: " + env.getJobPath)
        // Notify JobManager
        jm ! BinaryManagerFailure
        // Shut down
        context stop self
    }

    lastJmTs = System.currentTimeMillis()
    heartBeatChecker = Some(context.system.scheduler.schedule(
      0.milliseconds, binaryManagerTimeout.milliseconds,
      self, VerifyHeartbeat))
  }

  override def postStop(): Unit = {
    env.clean()
  }

  def receive = {
    case Connected(remote, local) =>
      val br = context.actorOf(BinaryReceiver(binaryReceiversId, env), "rec"+binaryReceiversId)
      binaryReceiversId += 1
      binaryReceivers.put(remote, br)
      sender() ! Register(br)
    case BinaryTransferComplete(remote) =>
      binaryReceivers.get(remote) match {
        case Some(ref) =>
          startExecutors(ref)
        case None =>
          log.error("Was not able to locate ref for remote: " + remote)
      }
    case BinariesCompiled =>
      // Binaries are ready to be transferred, open an Akka IO TCP
      // channel and let the JobManager know how to connect
      val askRef = sender()
      //TODO: fetch host from config
      IO(Tcp) ? Bind(self, new InetSocketAddress("localhost", 0)) onComplete {
        case Success(resp) => resp match {
          case Bound(localAddr) =>
            askRef ! BinaryTransferConn(localAddr)
          case CommandFailed(_ :Bind) =>
            askRef ! BinaryTransferError
        }
        case Failure(e) =>
          askRef ! BinaryTransferError
      }
    case VerifyHeartbeat =>
      val now = System.currentTimeMillis()
      val time = now - lastJmTs
      if (time > binaryManagerTimeout) {
        log.info("Did not receive communication from jobManager: " + jm + " within " + binaryManagerTimeout + " ms")
        log.info("Releasing slots: " + slots)
        shutdown()
      }
    case BMHeartBeat =>
      lastJmTs = System.currentTimeMillis()
    case Terminated(ref) =>
      executors = executors.filterNot(_ == ref)
      // TODO: handle scenario where not all executors have been started
      if (executors.isEmpty) {
        log.info("No alive BinaryExecutors left, releasing slots")
        shutdown()
      }
  }


  private def startExecutors(ref: ActorRef): Unit = {
    ref ? BinaryUploaded onComplete {
      case Success(resp) => resp match {
        case BinaryReady(binId) =>
          // improve names...
          job.job.tasks.foreach {task =>
            // Create 1 executor for each task
            val executor = context.actorOf(BinaryExecutor(env.getJobPath+"/" + binId, task, jm))
            executors = executors :+ executor
            // Enable DeathWatch
            context watch executor
          }
        case BinaryWriteFailure =>
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
