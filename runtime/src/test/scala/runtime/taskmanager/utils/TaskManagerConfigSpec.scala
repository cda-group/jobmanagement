package runtime.taskmanager.utils

import runtime.BaseSpec

class TaskManagerConfigSpec extends BaseSpec with TaskManagerConfig {

  "TaskManager Config" should "be functional" in {
    assert(config.isResolved)
    assert(slotTick > 0 && slotTick <= 15000)
    assert(nrOfSlots > 0 && nrOfSlots <= 100)
    assert(taskMasterTimeout > 0 && taskMasterTimeout <= 15000)
    assert(taskExecutorHealthCheck > 0 && taskExecutorHealthCheck <= 15000)
  }
}
