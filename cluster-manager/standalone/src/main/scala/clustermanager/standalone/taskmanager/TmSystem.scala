package clustermanager.standalone.taskmanager

import akka.actor.ActorSystem
import clustermanager.standalone.taskmanager.actors.ClusterListener
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import com.typesafe.scalalogging.LazyLogging
import runtime.common.Identifiers


class TmSystem extends App with TaskManagerConfig with LazyLogging {

  def start(): Unit = {
    logger.info("Starting up TaskManager")
    val system = ActorSystem(Identifiers.CLUSTER, config)
    val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

    system.whenTerminated
  }
}
