package runtime.resourcemanager

import akka.actor.ActorSystem
import runtime.common.Identifiers
import actors.ClusterListener
import com.typesafe.scalalogging.LazyLogging
import utils.RmConfig

object RmSystem extends App with RmConfig with LazyLogging {
  logger.info("Starting up ResourceManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
