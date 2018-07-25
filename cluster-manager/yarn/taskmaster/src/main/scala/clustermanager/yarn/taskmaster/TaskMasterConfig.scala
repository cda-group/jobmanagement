package clustermanager.yarn.taskmaster

import com.typesafe.config.ConfigFactory

trait TaskMasterConfig {
  val config = ConfigFactory.load("taskmaster.conf")
  val AMRMHeartbeatInterval = config.getInt("AMRMHeartbeatInterval")

  require(config.isResolved)
  require(AMRMHeartbeatInterval > 0, "Heartbeat interval between YARN's AM and RM has to be larger than 0")
}
