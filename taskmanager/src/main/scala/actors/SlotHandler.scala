package actors

import akka.actor.{Actor, ActorLogging, Props}
import common.TaskSlot

object SlotHandler {
  def apply(index: Int, taskSlot: TaskSlot): Props =
    Props(new SlotHandler(index, taskSlot))
}

class SlotHandler(index: Int, taskSlot: TaskSlot) extends Actor with ActorLogging {
  val slot = taskSlot

  // If some change has been made, notify parent TaskManager in order to update
  def receive = {
    //case AllocateTask
    //case RemoveTask
    //case AddTask
    case _ =>
  }
}
