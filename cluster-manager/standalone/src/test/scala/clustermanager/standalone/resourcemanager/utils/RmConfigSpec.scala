package clustermanager.standalone.resourcemanager.utils

import org.scalatest.FlatSpec


class RmConfigSpec extends FlatSpec with RmConfig {

  "ResourceManager Config" should "functional in" in {
    assert(config.isResolved)
    // to be extended
  }
}
