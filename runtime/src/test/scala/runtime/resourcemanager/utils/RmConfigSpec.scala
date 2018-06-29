package runtime.resourcemanager.utils

import runtime.BaseSpec

class RmConfigSpec extends BaseSpec with RmConfig{

  "ResourceManager Config" should "have functional config" in {
    assert(config.isResolved)
    // to be extended
  }
}
