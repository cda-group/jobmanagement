package runtime.statemanager.utils

import runtime.common.BaseSpec

class StateManagerConfigSpec extends BaseSpec with StateManagerConfig {

  "StateManager Config" should "be functional" in {
    assert(config.isResolved)
  }
}
