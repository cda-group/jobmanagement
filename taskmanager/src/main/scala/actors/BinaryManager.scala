package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import common._
import utils.{ExecutionEnvironment, TaskManagerConfig}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


object BinaryManager {
  def apply(job: ArcJob, slots: Seq[TaskSlot], jm: ActorRef):Props =
    Props(new BinaryManager(job, slots, jm))
  case object VerifyHeartbeat
}

/**
  * On a Successful ArcJob Allocation, a BinaryManager is created
  * to handle the incoming binaries from the JobManager once they have been compiled.
  * A BinaryManager expects heartbeats from the JobManager, if none are received within
  * the specified timeout, it will consider the job cancelled and instruct the
  * TaskManager to release the slots tied to it
  */
class BinaryManager(job: ArcJob, slots: Seq[TaskSlot], jm: ActorRef)
  extends Actor with ActorLogging with TaskManagerConfig {

  import BinaryManager._
  import context.dispatcher

  var heartBeatChecker = None: Option[Cancellable]
  var nrOfBinaries = 0
  val env = new ExecutionEnvironment(job)
  var lastJmTs: Long = 0

  var executors = mutable.IndexedSeq.empty[ActorRef]
  var executorId = 0

  override def preStart(): Unit = {
    lastJmTs = System.currentTimeMillis()
    heartBeatChecker = Some(context.system.scheduler.schedule(
      0.milliseconds, binaryManagerTimeout.milliseconds,
      self, VerifyHeartbeat))
  }

  override def postStop(): Unit = {
    env.clean()
  }

  def receive = {
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
    case BinaryJob(binaries) =>
      Try {
        env.create()
        binaries.foreach { b =>
          env.writeBinaryToFile(nrOfBinaries, b)
          nrOfBinaries += 1
        }
      } match {
        case Success(_) =>
          for (i <- 1 to binaries.size) {
            val executor = context.actorOf(BinaryExecutor(env.getJobPath+"/" + executorId),
              Identifiers.BINARY_EXECUTOR + executorId)

            executors = executors :+ executor
            executorId += 1

            // Enable DeathWatch
            context watch executor
          }
        case Failure(_) =>
      }
    case Terminated(ref) =>
      executors = executors.filterNot(_ == ref)
      if (executors.isEmpty) {
        log.info("No alive BinaryExecutors left, releasing slots")
        shutdown()
      }
    case _ =>
  }

  private def shutdown(): Unit = {
    // Cancel ticker
    heartBeatChecker.get
      .cancel()

    // Notify parent and shut down the actor
    context.parent ! ReleaseSlots(slots)
    context.stop(self)
  }
}
