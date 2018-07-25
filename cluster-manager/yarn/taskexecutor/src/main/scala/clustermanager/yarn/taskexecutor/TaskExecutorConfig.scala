package clustermanager.yarn.taskexecutor

import com.typesafe.config.ConfigFactory

trait TaskExecutorConfig {
  val config = ConfigFactory.load("executor.conf")
  val monitorInterval = config.getInt("monitor-interval")

  require(config.isResolved)
  require(monitorInterval > 0)
}
