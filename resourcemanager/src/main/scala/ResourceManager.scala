package resourcemanager

import actors.ClusterListener
import akka.actor.ActorSystem

object ResourceManager extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(ClusterListener(), "resourcemanager")

  system.whenTerminated
}
