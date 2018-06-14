package actors

import actors.ClusterListener.{TaskManagerRegistration, TaskManagerRemoved, UnreachableTaskManager}
import actors.ResourceManager.SlotRequest
import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.io.Udp.Send
import akka.pattern._
import akka.util.Timeout
import common.{AllocateFailure, AllocateSuccess, ArcJob, Utils}

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

  val activeJobManagers = mutable.HashSet[ActorRef]()
  val slotManager = context.actorOf(SlotManager(), Utils.SLOT_MANAGER)

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
    case job@ArcJob(_, _, ref) =>
      log.info("Got a job request from a JobManager")
      val askRef  = sender()
      ref match {
        case Some(jobManager) =>
          slotManager ? SlotRequest(job) onComplete {
            case Success(resp) => resp match {
              case resp@AllocateSuccess(j, slotHandler) =>
                log.info("JobManager Ref: " + jobManager)
                log.info("SlotHandler ref : " + slotHandler)
                // Allocation is successful, monitor the jobManager and slotHandler
                // If one of them dies, act accordingly
                //context watch jobManager
                // context watch slotHandler
                askRef ! resp
              case AllocateFailure(_) =>
                log.info("Allocate failure")
            }
            case Failure(e) =>
              log.info("Something went very wrong..")
              sender() ! "error" // change to something appropriate
          }
        case None =>
          log.error("JobManager Ref was not set in the ArcJob")
          // Notify
      }
    case _ =>
  }
}
