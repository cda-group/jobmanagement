package runtime.resourcemanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait RmConfig {
  val config = ConfigFactory.load("resourcemanager.conf")

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.RESOURCE_MANAGER),
    "ResourceManager role has not been set")
}
