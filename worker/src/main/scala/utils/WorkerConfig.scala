package utils

import com.typesafe.config.ConfigFactory

trait WorkerConfig {
  val config = ConfigFactory.load()
  val heartbeat = config.getLong("worker.heartbeat")
}
