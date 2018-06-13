package resourcemanager

import actors.ClusterListener
import akka.actor.ActorSystem
import common.Utils

object RmSystem extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(ClusterListener(), Utils.LISTENER)

  system.whenTerminated
}
