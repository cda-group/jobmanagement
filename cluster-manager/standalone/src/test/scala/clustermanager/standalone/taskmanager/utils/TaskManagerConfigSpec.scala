package clustermanager.standalone.taskmanager.utils

import org.scalatest.FlatSpec

class TaskManagerConfigSpec extends FlatSpec with TaskManagerConfig {

  import org.scalatest.FlatSpec

  "TaskManager Config" should "be functional" in {
    assert(config.isResolved)
    //assert(slotTick > 0 && slotTick <= 15000)
    //assert(nrOfSlices > 0 && nrOfSlices <= 100)
    assert(taskMasterTimeout > 0 && taskMasterTimeout <= 15000)
    assert(taskExecutorHealthCheck > 0 && taskExecutorHealthCheck <= 15000)
  }
}
