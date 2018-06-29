package runtime.resourcemanager.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import akka.util.Timeout
import runtime.common._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ResourceManager {
  def apply(): Props = Props(new ResourceManager)
  case class SlotRequest(job: ArcJob)
}

/**
  * The ResourceManager is responsible for handling the
  * computing resources in the Arc Cluster.
  * 1. Receives Jobs from JobManagers
  * 2. Utilises a SlotManager in order to keep track of free slots
  */
class ResourceManager extends Actor with ActorLogging {

  import ClusterListener._
  import ResourceManager._
  import runtime.common.Types._

  val activeJobManagers = mutable.HashSet[JobManagerRef]()
  val slotManager = context.actorOf(SlotManager(), Identifiers.SLOT_MANAGER)

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case tmr@TaskManagerRegistration(_) =>
      slotManager forward tmr
    case tmr@TaskManagerRemoved(_) =>
      slotManager forward tmr
    case utm@UnreachableTaskManager(_) =>
      slotManager forward utm
    case job@ArcJob(_, _, _,  ref) =>
      log.info("Got a job request from a JobManager")
      val askRef  = sender()
      ref match {
        case Some(jobManager) =>
          slotManager ? SlotRequest(job) onComplete {
            case Success(resp) => resp match {
              case resp@AllocateSuccess(j, taskManagerRef) =>
                askRef ! resp
              case resp@AllocateFailure(_) =>
                askRef ! resp
            }
            case Failure(e) =>
              sender() ! AllocateFailure(UnexpectedError)
          }
        case None =>
          log.error("JobManager Ref was not set in the ArcJob")
          // Notify
      }
    case _ =>
  }
}
