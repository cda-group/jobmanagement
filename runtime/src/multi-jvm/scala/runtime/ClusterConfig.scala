package runtime

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory
import runtime.common.Identifiers

object ClusterConfig extends MultiNodeConfig {
  val taskmanager = role(Identifiers.TASK_MANAGER)
  val resourcemanager = role(Identifiers.RESOURCE_MANAGER)
  val driver = role(Identifiers.DRIVER)


  nodeConfig(taskmanager)(ConfigFactory.parseString(
    s"""
      |akka.cluster.roles=[${Identifiers.TASK_MANAGER}]
    """.stripMargin))

  nodeConfig(resourcemanager)(ConfigFactory.parseString(
    s"""
       |akka.cluster.roles=[${Identifiers.RESOURCE_MANAGER}]
    """.stripMargin))

  nodeConfig(driver)(ConfigFactory.parseString(
    s"""
       |akka.cluster.roles=[${Identifiers.DRIVER}]
    """.stripMargin))

  commonConfig(ConfigFactory.parseString(
    """
      |akka.loglevel=OFF
      |akka.actor.provider = cluster
      |akka.cluster.log-info = off
      |akka.log-dead-letters=off
      |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      |akka.coordinated-shutdown.terminate-actor-system = off
      |akka.cluster.run-coordinated-shutdown-when-down = off
    """.stripMargin))
}
