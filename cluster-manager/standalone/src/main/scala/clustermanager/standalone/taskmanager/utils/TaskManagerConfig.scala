package clustermanager.standalone.taskmanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait TaskManagerConfig {
  val config = ConfigFactory.load("taskmanager.conf")
  val slotTick = config.getLong("taskmanager.slotUpdateTick")
  val taskMasterTimeout = config.getLong("taskmanager.taskMasterTimeout")
  val taskExecutorHealthCheck = config.getLong("taskmanager.taskExecutorHealthCheck")
  val hostname = config.getString("taskmanager.hostname")

  val isolation = config.getString("taskmanager.isolation")
  val resourceLimit = config.getDouble("taskmanager.resource-limit")

  require(slotTick > 0)
  require(taskMasterTimeout > 0)
  require(taskExecutorHealthCheck > 0 )
  require(!hostname.isEmpty)
  require(resourceLimit > 0 && resourceLimit < 100)

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.TASK_MANAGER),
    "TaskManager role has not been set")
}
