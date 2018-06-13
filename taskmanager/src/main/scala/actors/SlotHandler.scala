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
      sender() ! AllocateSuccess(job)
    case Allocate(_) =>
      sender() ! AllocateFailure(slot.state)
    //case ReleaseSlot()
    //case RemoveArcTask
    //case AddArcTask
    case _ =>
  }
}
