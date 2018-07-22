package clustermanager.standalone.taskmanager.utils

import clustermanager.common.{OperatingSystem, Unknown}
import org.scalatest.FlatSpec

class OperatingSystemSpec extends FlatSpec {

  "Operating System" should "not equal unknown" in {
    assert(OperatingSystem.get() != Unknown)
  }
}
