package clustermanager.standalone.taskmanager.utils

import org.scalatest.FlatSpec

class TaskManagerConfigSpec extends FlatSpec with TaskManagerConfig {


  "TaskManager Config" should "be functional" in {
    assert(config.isResolved)
<<<<<<< HEAD
    assert(sliceTick > 0 && sliceTick <= 15000)
    assert(nrOfSlices > 0 && nrOfSlices <= 100)
=======
    //assert(slotTick > 0 && slotTick <= 15000)
    //assert(nrOfSlices > 0 && nrOfSlices <= 100)
>>>>>>> 383ddc6f393cf1d3f6818806cafee905261180b0
    assert(taskMasterTimeout > 0 && taskMasterTimeout <= 15000)
    assert(taskExecutorHealthCheck > 0 && taskExecutorHealthCheck <= 15000)
  }
}
