package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import common.{BinaryManagerInit, ReleaseSlots, TaskSlot}
import utils.TaskManagerConfig

import scala.concurrent.duration._


object BinaryManager {
  def apply(slots: Seq[TaskSlot], jm: ActorRef):Props =
    Props(new BinaryManager(slots, jm))
  case object Verify
}

/**
  * On a Successful ArcJob Allocation, a BinaryManager is created
  * to handle the incoming binaries from the JobManager once they have been compiled.
  * A BinaryManager is initialized with a timeout, if it has not received contact from
  * its JobManager within that timeout, it will consider the job cancelled and instruct
  * the TaskManager to release the slots tied to it
  */
class BinaryManager(slots: Seq[TaskSlot], jm: ActorRef)
  extends Actor with ActorLogging with TaskManagerConfig {

  import BinaryManager._
  import context.dispatcher

  var active = false

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(binaryManagerTimeout.milliseconds, self, Verify)
  }

  def receive = {
    case Verify if !active =>
      log.info("Did not receive communication from jobManager: " + jm + " within " + binaryManagerTimeout + " ms")
      log.info("Releasing slots: " + slots)
      context.parent ! ReleaseSlots(slots)
      context.stop(self)
    case Verify =>
      log.info("BinaryManager is in contact with its JobManager: " + jm)
    case BinaryManagerInit =>
      active = true
    case _ =>
  }
}
