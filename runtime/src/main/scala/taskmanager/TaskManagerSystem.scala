package taskmanager

import akka.actor.ActorSystem
import common.Identifiers
import actors.ClusterListener
import utils.{Hardware, TaskManagerConfig}

object TaskManagerSystem extends App with TaskManagerConfig {
  val system = ActorSystem("JmCluster", config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  println(Hardware.getSizeOfPhysicalMemory)
  println(Hardware.getNumberCPUCores)
  system.whenTerminated
}
