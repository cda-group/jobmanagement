package actors

import akka.actor.{Actor, ActorLogging, Address, Props, RootActorPath}
import common.{JobRequest, Utils, WorkerInit, WorkerState}

import scala.collection.mutable

object JobMaster {
  def apply(): Props = Props(new JobMaster)
}

class JobMaster extends Actor with ActorLogging {
  import ClusterListener._

  val workers = mutable.HashSet[Address]()
  val workerStates = mutable.HashMap[Address, WorkerState]()

  def receive = {
    case WorkerRegistration(worker) =>
      workers += worker
      val target = context.actorSelection(Utils.workerPath(worker))
      // TODO: add retry logic in case worker is not reachable
      target ! WorkerInit
    case WorkerRemoved(worker) =>
      workers.remove(worker)
    case s@WorkerState(_,_) =>
      workerStates.put(sender().path.address, s)
    case UnreachableWorker(worker) =>
      // For now
      workers.remove(worker)
      workerStates.remove(worker)
    case JobRequest(id) =>
      log.info("Got a job request from a driver")
    case _ =>
  }

}
