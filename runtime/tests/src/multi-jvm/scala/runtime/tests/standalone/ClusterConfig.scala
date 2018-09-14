package runtime.tests

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory
import runtime.common.Identifiers

object ClusterConfig extends MultiNodeConfig {
  val taskmanager = role(Identifiers.TASK_MANAGER)
  val resourcemanager = role(Identifiers.RESOURCE_MANAGER)
  val appmanager = role(Identifiers.APP_MANAGER)
  val statemanager = role(Identifiers.STATE_MANAGER)

  nodeConfig(taskmanager)(ConfigFactory.parseString(
    s"""
       |akka.cluster.roles=[${Identifiers.TASK_MANAGER}]
       |akka.cluster.metrics.native-library-extract-folder=$${user.dir}/target/native/${Identifiers.TASK_MANAGER}
    """.stripMargin))

  nodeConfig(resourcemanager)(ConfigFactory.parseString(
    s"""
        |akka.cluster.roles=[${Identifiers.RESOURCE_MANAGER}]
    """.stripMargin))

  nodeConfig(appmanager)(ConfigFactory.parseString(
    s"""
       |akka.cluster.roles=[${Identifiers.APP_MANAGER}]
       |akka.cluster.metrics.native-library-extract-folder=$${user.dir}/target/native/${Identifiers.APP_MANAGER}
    """.stripMargin))

  nodeConfig(statemanager)(ConfigFactory.parseString(
    s"""
       |akka.cluster.roles=[${Identifiers.STATE_MANAGER}]
       |akka.cluster.metrics.native-library-extract-folder=$${user.dir}/target/native/${Identifiers.STATE_MANAGER}
    """.stripMargin))


  commonConfig(ConfigFactory.parseString(
    """
      |akka.loglevel = off
      |akka.remote.log-remote-lifecycle-events = off
      |akka.actor.provider = cluster
      |akka.cluster.log-info = off
      |akka.stdout-loglevel = off
      |akka.actor.serializers.proto = "runtime.protobuf.ProtobufSerializer"
      |akka.actor.serializers.java = "akka.serialization.JavaSerializer"
      |akka.actor.serialize-runtime.protobuf.messages = on
      |akka.actor.serialization-bindings {"scalapb.GeneratedMessage" = proto}
      |akka.log-dead-letters = off
      |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      |akka.coordinated-shutdown.terminate-actor-system = off
      |akka.cluster.run-coordinated-shutdown-when-down = off
    """.stripMargin))
}
