package clustermanager.standalone.taskmanager.utils

import runtime.common.BaseSpec
import runtime.taskmaster.common.{OperatingSystem, Unknown}

class OperatingSystemSpec extends BaseSpec {

  "Operating System" should "not equal unknown" in {
    assert(OperatingSystem.get() != Unknown)
  }
}
