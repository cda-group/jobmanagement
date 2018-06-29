package runtime.taskmanager.utils

import runtime.BaseSpec

class TaskManagerConfigSpec extends BaseSpec with TaskManagerConfig {

  "TaskManager Config" should "have functional config" in {
    assert(config.isResolved)
    assert(slotTick > 0 && slotTick <= 15000)
    assert(nrOfSlots > 0 && nrOfSlots <= 100)
    assert(binaryManagerTimeout > 0 && binaryManagerTimeout <= 15000)
    assert(binaryExecutorHealthCheck > 0 && binaryExecutorHealthCheck <= 15000)
  }
}
