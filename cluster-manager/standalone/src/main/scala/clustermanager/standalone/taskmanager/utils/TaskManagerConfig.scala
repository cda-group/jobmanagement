package clustermanager.standalone.taskmanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait TaskManagerConfig {
  val config = ConfigFactory.load("taskmanager.conf")
  val sliceTick = config.getLong("taskmanager.sliceUpdateTick")
  val taskMasterTimeout = config.getLong("taskmanager.taskMasterTimeout")
  val taskExecutorHealthCheck = config.getLong("taskmanager.taskExecutorHealthCheck")
  val hostname = config.getString("taskmanager.hostname")

  require(sliceTick > 0)
  val isolation = config.getString("taskmanager.isolation")
  val resourceLimit = config.getDouble("taskmanager.resource-limit")
  val cgroupsPath = config.getString("taskmanager.cgroups-path")

  require(taskMasterTimeout > 0)
  require(taskExecutorHealthCheck > 0 )
  require(!hostname.isEmpty)
  require(resourceLimit > 0 && resourceLimit < 100)

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.TASK_MANAGER),
    "TaskManager role has not been set")
}
