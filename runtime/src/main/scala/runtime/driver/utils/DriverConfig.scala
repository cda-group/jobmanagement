package runtime.driver.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait DriverConfig {
  val config = ConfigFactory.load("driver.conf")
  val jobManagerKeepAlive = config.getLong("driver.jobManagerKeepAlive")
  val restPort = config.getInt("driver.restPort")
  val interface = config.getString("driver.interface")

  require(config.isResolved)
  require(jobManagerKeepAlive > 0)
  require(restPort >= 0 && restPort <= 65535)
  require(!interface.isEmpty)

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.DRIVER), "Driver role has not been set")
}
