package actors

import actors.TaskManager.TaskSlotUpdate
import akka.actor.{Actor, ActorLogging, Props}
import common._

object SlotHandler {
  def apply(index: Int, taskSlot: TaskSlot): Props =
    Props(new SlotHandler(index, taskSlot))
}

/**
  * The SlotHandler is responsible for ArcTasks in the
  * given Task Slot index
  * @param index Slot Index
  * @param taskSlot The TaskSlot the handler is responsible for
  */
class SlotHandler(index: Int, taskSlot: TaskSlot) extends Actor with ActorLogging {
  var slot = taskSlot
  var jobId = None: Option[String]

  def receive = {
    case Allocate(job) if slot.state == Free =>
      slot = slot.newState(Allocated)
      context.parent ! TaskSlotUpdate(index, slot.copy())
      jobId = Some(job.id)
      sender() ! AllocateSuccess(job, self)
    case Allocate(_) =>
      sender() ! AllocateFailure(slot.state)
    case req@AddArcTask(id, task) =>
      jobId match {
        case Some(_id) =>
          if (_id == id) {
            // JobId is correct, the JobManager has permission to do operations
          } else {
            // Wrong JobId provided, let JobManager know..
            // sender() ! WrongJobID
          }
      }
      log.info("Received ArcTask: "+ req)
    case RemoveArcTask(id, task) =>
    case ReleaseSlot() =>
    case _ =>
  }
}
