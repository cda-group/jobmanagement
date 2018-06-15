package utils

import com.typesafe.config.ConfigFactory

trait TaskManagerConfig {
  val config = ConfigFactory.load()
  val slotTick = config.getLong("taskmanager.slotUpdateTick")
  val nrOfSlots = config.getInt("taskmanager.slots")
  val binaryManagerTimeout = config.getLong("taskmanager.binaryManagerTimeout")
}
