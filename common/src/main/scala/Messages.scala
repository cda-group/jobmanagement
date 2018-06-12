package common


// to start of with
case class JobRequest(id: String)
case class JobAck(id: String)


case class WorkerState(cpu: Long, mem: Long)
case object WorkerRegistration
case object TaskManagerInit


case class SlotAvailibility(slots: Set[TaskSlot])
case class TaskSlot(index: Int, profile: ResourceProfile, state: SlotState = Free) {

}

case class HardwareResources(cores: Int,
                             physicalMemSize: Long,
                             jvmHeapMemSize: Long,
                             managedMemSize: Long)

case class ResourceProfile(cpuCores: Double, heapMemoryMB: Int,
                           directMemoryMB: Int, nativeMemoryMB: Int)


