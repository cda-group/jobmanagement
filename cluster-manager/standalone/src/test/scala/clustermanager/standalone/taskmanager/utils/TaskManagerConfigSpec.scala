package clustermanager.standalone.taskmanager.utils

import org.scalatest.FlatSpec

class TaskManagerConfigSpec extends FlatSpec with TaskManagerConfig {


  "TaskManager Config" should "be functional" in {
    assert(config.isResolved)
    assert(sliceTick > 0 && sliceTick <= 15000)
    assert(nrOfSlices > 0 && nrOfSlices <= 100)
    assert(taskMasterTimeout > 0 && taskMasterTimeout <= 15000)
    assert(taskExecutorHealthCheck > 0 && taskExecutorHealthCheck <= 15000)
  }
}
