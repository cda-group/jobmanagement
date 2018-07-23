package runtime.statemanager.utils

import org.scalatest.FlatSpec

class StateManagerConfigSpec extends FlatSpec with StateManagerConfig {

  "StateManager Config" should "be functional" in {
    assert(config.isResolved)
  }
}
