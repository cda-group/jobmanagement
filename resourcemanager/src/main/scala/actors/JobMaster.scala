package actors

import actors.JobMaster.{WorkerHandlerInit, WorkerLoss}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import common.{Test, WorkerState}

import scala.collection.mutable

object JobMaster {
  def apply(): Props = Props(new JobMaster)
  case class WorkerHandlerInit(worker: ActorRef)
  case class StateUpdate(worker: ActorRef, state: WorkerState)
  case object WorkerStateRequest
  case object WorkerLoss
}

class JobMaster extends Actor with ActorLogging {
  import ClusterListener._
  import JobMaster._

  type Worker = ActorRef
  type Child = ActorRef

  val workers = mutable.HashSet[ActorRef]()
  val heartbeatHandlers = mutable.HashMap[Child, Worker]()

  def receive = {
    case WorkerRegistration(worker) =>
      workers += worker
      log.info("Added worker: " + worker)
      val child = context.actorOf(WorkerHandler()) // Add unique actor name?
      heartbeatHandlers.put(child, worker)
      child ! WorkerHandlerInit(worker)
    case WorkerRemoved(worker) =>
      // Check if they exist before removing...
      workers.remove(worker)
      //heartbeatHandlers.remove(worker)
    case WorkerLoss =>
      //heartbeatHandlers.getOrElse(sender())
    //case JobRequest
    //case JobRequestReply
    case _ =>
  }

}
