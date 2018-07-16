package clustermanager.standalone.taskmanager

import akka.actor.ActorSystem
import actors.ClusterListener
import com.typesafe.scalalogging.LazyLogging
import utils.TaskManagerConfig
import runtime.common.Identifiers

object TmSystem extends App with TaskManagerConfig with LazyLogging {
  logger.info("Starting up TaskManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
