package resourcemanager

import actors.ClusterListener
import akka.actor.ActorSystem
import common.Identifiers

object RmSystem extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
