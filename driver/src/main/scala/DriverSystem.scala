package driver

import java.nio.file.{Files, Paths, StandardOpenOption}

import actors.ClusterListener
import akka.actor.ActorSystem
import common.Identifiers

object DriverSystem extends App {
  val system = ActorSystem("JmCluster")
  val driver = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  val file = Files.readAllBytes(Paths.get("../writetofile"))


  system.whenTerminated
}
