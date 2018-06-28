package runtime.taskmanager

import runtime.BaseSpec
import utils.{OperatingSystem, Unknown}

class OperatingSystemSpec extends BaseSpec {

  "Operating System" should "not equal unknown" in {
    assert(OperatingSystem.get() != Unknown)
  }
}
