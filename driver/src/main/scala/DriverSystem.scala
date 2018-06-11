package driver

import actors.Driver
import akka.actor.ActorSystem

object DriverSystem extends App {
  val system = ActorSystem("JmCluster")
  val driver = system.actorOf(Driver(), "driver")
  system.whenTerminated
}
