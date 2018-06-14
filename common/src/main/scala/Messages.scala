package common

import akka.actor.ActorRef


case class ArcJob(id: String, profile: ArcProfile, jobManagerRef: Option[ActorRef] = None)
case class ArcJobRequest(job: ArcJob)
case class ArcTask(id: String) // to define
case class ArcProfile(cpuCores: Double, memoryInMB: Long) {
  def matches(other: ArcProfile): Boolean =
    other.cpuCores <= this.cpuCores && other.memoryInMB <= this.memoryInMB
}

case object TaskManagerInit

case class TaskSlot(index: Int, profile: ArcProfile, state: SlotState = Free) {
  def newState(newState: SlotState): TaskSlot = {
    this.copy(state = newState)
  }
}
case class SlotUpdate(slots: Set[TaskSlot])
case object SlotAvailability

/*
case class HardwareResources(cores: Int,
                             physicalMemSize: Long,
                             jvmHeapMemSize: Long,
                             managedMemSize: Long)
                             */

// SlotHandler
case class Allocate(job: ArcJob)
case class AllocateSuccess(job: ArcJob, slotHandlerRef: ActorRef)
case class AllocateFailure(slotState: SlotState)
case class AddArcTask(jobId: String, task: ArcTask)
case class RemoveArcTask(jobId: String, arcTask: ArcTask)
case class ReleaseSlot()


