package driver

import actors.{ClusterListener, Driver}
import akka.actor.ActorSystem

object DriverSystem extends App {
  val system = ActorSystem("JmCluster")
  val driver = system.actorOf(ClusterListener(), "listener")
  system.whenTerminated
}
