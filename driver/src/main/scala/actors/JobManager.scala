package actors

import java.nio.file.{Files, Paths}

import actors.Driver.JobManagerInit
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.util.Timeout
import akka.pattern._
import common._
import utils.DriverConfig

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object JobManager {
  def apply(): Props = Props(new JobManager)
}

/**
  * One JobManager is created per ArcJob. If a slot allocation is successful,
  * the JobManager communicates directly with the BinaryManager and can do the following:
  * 1. Instruct BinaryManager to execute binaries
  * 2. Release the slots
  */
class JobManager extends Actor with ActorLogging with DriverConfig {
  var binaryManager = None: Option[ActorRef]
  var keepAliveTicker = None: Option[Cancellable]

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case JobManagerInit(job, rmAddr) =>
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      resourceManager ? job.copy(jobManagerRef = Some(self)) onComplete {
        case Success(resp) =>
          resp match {
            case AllocateSuccess(job_, bm) =>
              log.info("Jobmanager allocated slot successfully")
              keepAliveTicker = keepAlive(bm)
              binaryManager = Some(bm)
              bm ! BinaryJob(Seq(testBinary))
            case AllocateFailure(_) =>
              log.info("Jobmanager failed to allocate slot")
              // Failure for some reason. A TaskSlot was most likely not in a Free state..
              // context.parent ! notify
          }
        case Failure(e) =>
          log.info("failure of ArcJob: " + e.toString)
        // context.parent ! We failed
        // context.stop() // Kill this actor
      }
    case r@ReleaseSlots =>
      binaryManager match {
        case Some(ref) =>
          ref ! r
        case None =>
          //sender() ! no binaryManager defined "handle"
      }
    case _ =>
  }

  private def keepAlive(binaryManager: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      jobManagerKeepAlive.milliseconds) {
      binaryManager ! BMHeartBeat
    })

  }

  private def testBinary(): Array[Byte] =
    Files.readAllBytes(Paths.get("../writetofile"))
}
