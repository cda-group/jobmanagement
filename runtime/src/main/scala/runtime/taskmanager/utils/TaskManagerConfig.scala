package runtime.taskmanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait TaskManagerConfig {
  val config = ConfigFactory.load("taskmanager.conf")
  val slotTick = config.getLong("taskmanager.slotUpdateTick")
  val nrOfSlots = config.getInt("taskmanager.slots")
  val binaryManagerTimeout = config.getLong("taskmanager.binaryManagerTimeout")
  val binaryExecutorHealthCheck = config.getLong("taskmanager.binaryExecutorHealthCheck")

  require(slotTick > 0)
  require(nrOfSlots > 0)
  require(binaryManagerTimeout > 0)
  require(binaryExecutorHealthCheck > 0 )

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.TASK_MANAGER),
    "TaskManager role has not been set")
}
