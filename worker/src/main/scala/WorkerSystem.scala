package worker

import actors.Worker
import akka.actor.ActorSystem

object WorkerSystem extends App {
  val system = ActorSystem("JmCluster")
  val handler = system.actorOf(Worker(), "worker")

  system.whenTerminated
}
