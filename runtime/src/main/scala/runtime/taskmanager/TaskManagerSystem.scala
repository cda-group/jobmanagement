package runtime.taskmanager

import akka.actor.ActorSystem
import runtime.common.Identifiers
import actors.ClusterListener
import com.typesafe.scalalogging.LazyLogging
import utils.TaskManagerConfig

object TaskManagerSystem extends App with TaskManagerConfig with LazyLogging {
  logger.info("Starting up TaskManager")
  val system = ActorSystem("JmCluster", config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
