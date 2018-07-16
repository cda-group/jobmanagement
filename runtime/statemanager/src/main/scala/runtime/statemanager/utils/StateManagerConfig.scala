package runtime.statemanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait StateManagerConfig {
  val config = ConfigFactory.load("statemanager.conf")

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.STATE_MANAGER),
    "StateManager role has not been set")
}
