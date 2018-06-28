package runtime.driver


import actors.ClusterListener
import akka.actor.ActorSystem
import runtime.common.Identifiers
import utils.DriverConfig

object DriverSystem extends App with DriverConfig {
  val system = ActorSystem("JmCluster", config)
  val driver = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
