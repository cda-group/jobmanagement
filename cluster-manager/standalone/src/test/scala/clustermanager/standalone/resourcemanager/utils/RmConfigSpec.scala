package clustermanager.standalone.resourcemanager.utils

import runtime.common.BaseSpec


class RmConfigSpec extends BaseSpec with RmConfig{

  "ResourceManager Config" should "functional in" in {
    assert(config.isResolved)
    // to be extended
  }
}
