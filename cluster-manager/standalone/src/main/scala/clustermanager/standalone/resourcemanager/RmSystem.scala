package clustermanager.standalone.resourcemanager

import akka.actor.ActorSystem
import clustermanager.standalone.resourcemanager.actors.ClusterListener
import clustermanager.standalone.resourcemanager.utils.RmConfig
import com.typesafe.scalalogging.LazyLogging
import runtime.common.Identifiers

class RmSystem extends App with RmConfig with LazyLogging {

  def start(): Unit = {
    logger.info("Starting up ResourceManager")
    val system = ActorSystem(Identifiers.CLUSTER, config)
    val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

    system.whenTerminated
  }
}
