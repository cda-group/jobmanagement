package actors

import actors.JobMaster.{WorkerHandlerInit, WorkerStateRequest}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, RootActorPath}
import common.{HeartbeatInit, WorkerState}

import scala.collection.mutable

object WorkerHandler {
  def apply(): Props = Props(new WorkerHandler)
}

class WorkerHandler extends Actor with ActorLogging {

  // TODO: Add heartbeat logic
  // if none received within X time, notify parent (JobMaster)

  val state = mutable.HashMap[ActorRef, WorkerState]()
  var worker = None: Option[ActorRef]

  def receive = {
    case WorkerHandlerInit(w) =>
      log.info("I am initiated")
      worker = Some(w)
      log.info("Sending heartbeatinit to: " + worker.get.path)
      context.actorSelection(RootActorPath(worker.get.path.address) / "user" / "worker") ! HeartbeatInit
      // Start ticker for WorkerState
    case s@WorkerState(_, _) => // Oh we got an update, how nice
      log.info("HeartBeat Received from: " + sender())
      state.put(sender(), s)
    case WorkerStateRequest => // JobMaster is requesting latest state of the worker
      worker match {
        case Some(ref) => context.parent ! state.get(ref)
        case None => // Something went very wrong, let parent know and kill this actor
      }
    case _ =>
  }

}
