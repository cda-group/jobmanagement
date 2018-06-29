package runtime.taskmanager.utils

import runtime.BaseSpec

class OperatingSystemSpec extends BaseSpec {

  "Operating System" should "not equal unknown" in {
    assert(OperatingSystem.get() != Unknown)
  }
}
