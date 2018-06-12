package worker

import actors.ClusterListener
import akka.actor.ActorSystem
import utils.Hardware

object TaskManagerSystem extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(ClusterListener(), "listener")

  println(Hardware.getSizeOfPhysicalMemory)
  println(Hardware.getNumberCPUCores)

  system.whenTerminated
}
