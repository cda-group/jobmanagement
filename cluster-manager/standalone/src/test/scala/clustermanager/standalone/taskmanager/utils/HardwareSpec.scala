package clustermanager.standalone.taskmanager.utils

import clustermanager.common.Hardware
import org.scalatest.FlatSpec


class HardwareSpec extends FlatSpec {

  "CPU cores" should "have a size larger than 0" in {
    assert(Hardware.getNumberCPUCores > 0)
  }

  "Physical memory" should "have a size larger than 0" in {
    assert(Hardware.getSizeOfPhysicalMemory > 0)
  }
}
