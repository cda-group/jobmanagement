package driver

import actors.{ClusterListener, Driver}
import akka.actor.ActorSystem
import common.Utils

object DriverSystem extends App {
  val system = ActorSystem("JmCluster")
  val driver = system.actorOf(ClusterListener(), Utils.LISTENER)
  system.whenTerminated
}
