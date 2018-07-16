package clustermanager.standalone.resourcemanager

import akka.actor.ActorSystem
import actors.ClusterListener
import com.typesafe.scalalogging.LazyLogging
import runtime.common.Identifiers
import utils.RmConfig

object RmSystem extends App with RmConfig with LazyLogging {
  logger.info("Starting up ResourceManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
