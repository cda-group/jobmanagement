package runtime.taskmanager.utils

import runtime.BaseSpec


class HardwareSpec extends BaseSpec {

  "CPU cores" should "have a size larger than 0" in {
    assert(Hardware.getNumberCPUCores > 0)
  }

  "Physical memory" should "have a size larger than 0" in {
    assert(Hardware.getSizeOfPhysicalMemory > 0)
  }
}
