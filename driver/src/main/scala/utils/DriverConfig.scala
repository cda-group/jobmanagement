package utils

import com.typesafe.config.ConfigFactory

trait DriverConfig {
  val config = ConfigFactory.load()
  val jobManagerKeepAlive = config.getLong("driver.jobManagerKeepAlive")
}
