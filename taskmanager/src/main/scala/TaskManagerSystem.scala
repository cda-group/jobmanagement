package taskmanager

import actors.ClusterListener
import akka.actor.ActorSystem
import common.Utils
import utils.Hardware

object TaskManagerSystem extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(ClusterListener(), Utils.LISTENER)

  println(Hardware.getSizeOfPhysicalMemory)
  println(Hardware.getNumberCPUCores)

  system.whenTerminated
}
