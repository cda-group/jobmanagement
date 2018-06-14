package actors

import actors.Driver.JobManagerInit
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import akka.pattern._
import common._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object JobManager {
  def apply(): Props = Props(new JobManager)
}

/**
  * One JobManager is created per ArcJob. If a slot allocation is successful,
  * the JobManager communicates directly with the SlotHandler and can do the following:
  * 1. Add ArcTasks to the TaskSlot
  * 2. Remove ArcTasks from the TaskSlot
  * 3. Release the TaskSlot
  */
class JobManager extends Actor with ActorLogging {

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case JobManagerInit(job, rmAddr) =>
      log.info("JobManagerInit")
      val resourceManager = context.actorSelection(Utils.resourceManagerPath(rmAddr))
      resourceManager ? job.copy(jobManagerRef = Some(self)) onComplete {
        case Success(resp) =>
          resp match {
            case AllocateSuccess(job_, slotHandler) =>
              log.info("Jobmanager allocated slot successfully")
              // On success, we are free to add ArcTasks to the slotHandler
              slotHandler ! AddArcTask(job.id, ArcTask("id"))
            case AllocateFailure(_) =>
              log.info("Jobmanager failed to allocate slot")
              // Failure for some reason. TaskSlot was most likely not in a Free state..
              // context.parent ! notify
          }
        case Failure(e) =>
          log.info("failure of ArcJob: " + e.toString)
        // context.parent ! We failed
        // context.stop() // Kill this actor
      }
    case _ =>
  }
}
