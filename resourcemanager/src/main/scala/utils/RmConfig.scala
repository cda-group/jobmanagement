package utils

import com.typesafe.config.ConfigFactory

trait RmConfig {
  val config = ConfigFactory.load()
}
