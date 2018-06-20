package common

import akka.actor.ActorRef


case class ArcJob(id: String, profile: ArcProfile, jobManagerRef: Option[ActorRef] = None)
case class ArcJobRequest(job: ArcJob)
case class ArcProfile(cpuCores: Double, memoryInMB: Long) {
  def matches(other: ArcProfile): Boolean =
    this.cpuCores >= other.cpuCores && this.memoryInMB >= other.memoryInMB
}



case object BMHeartBeat
case class BinaryJob(binaries: Seq[Array[Byte]])


// TaskManager
case object TaskManagerInit
case class Allocate(job: ArcJob, slots: Seq[TaskSlot])
case class AllocateSuccess(job: ArcJob, ref: ActorRef)
case class AllocateFailure(resp: SlotRequestResp)
case class ReleaseSlots(slotIndxes: Seq[Int])
case class SlotUpdate(slots: Seq[TaskSlot])
case class BinaryManagerInit()

case class TaskSlot(index: Int, profile: ArcProfile, state: SlotState = Free) {
  def newState(s: SlotState): TaskSlot = {
    this.copy(state = s)
  }
}


