package runtime.resourcemanager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import runtime.common.Identifiers
import actors.ClusterListener
import utils.RmConfig

object RmSystem extends App with RmConfig {
  val system = ActorSystem("JmCluster", config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
