package clustermanager.standalone.taskmanager.utils

import com.typesafe.config.{ConfigFactory, ConfigList}
import runtime.common.Identifiers

trait TaskManagerConfig {
  val config = ConfigFactory.load("taskmanager.conf")
  val sliceTick = config.getLong("taskmanager.sliceUpdateTick")
  val nrOfSlices = config.getInt("taskmanager.slices")
  val taskMasterTimeout = config.getLong("taskmanager.taskMasterTimeout")
  val taskExecutorHealthCheck = config.getLong("taskmanager.taskExecutorHealthCheck")
  val hostname = config.getString("taskmanager.hostname")

  require(sliceTick > 0)
  require(nrOfSlices > 0)
  require(taskMasterTimeout > 0)
  require(taskExecutorHealthCheck > 0 )
  require(!hostname.isEmpty)

  val roles: ConfigList = config.getList("akka.cluster.roles")
  require(roles.unwrapped().contains(Identifiers.TASK_MANAGER),
    "TaskManager role has not been set")
}
