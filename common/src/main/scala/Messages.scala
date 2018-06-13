package common



case class ArcJob(id: String, resourceProfile: ResourceProfile)
case class ArcTask()

case object TaskManagerInit

case class TaskSlot(index: Int, profile: ResourceProfile, state: SlotState = Free) {
  def newState(newState: SlotState): TaskSlot = {
    this.copy(state = newState)
  }
}
case class SlotUpdate(slots: Set[TaskSlot])
case object SlotAvailability

case class HardwareResources(cores: Int,
                             physicalMemSize: Long,
                             jvmHeapMemSize: Long,
                             managedMemSize: Long)

case class ResourceProfile(cpuCores: Double, memoryInMB: Long) {
  def matches(other: ResourceProfile): Boolean =
    other.cpuCores <= this.cpuCores && other.memoryInMB <= this.memoryInMB
}

// SlotHandler
case class Allocate(job: ArcJob)
case class AllocateSuccess(job: ArcJob)
case class AllocateFailure(slotState: SlotState)


