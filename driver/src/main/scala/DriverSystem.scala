package driver

import actors.ClusterListener
import akka.actor.ActorSystem
import common.Identifiers

object DriverSystem extends App {
  val system = ActorSystem("JmCluster")
  val driver = system.actorOf(ClusterListener(), Identifiers.LISTENER)
  system.whenTerminated
}
