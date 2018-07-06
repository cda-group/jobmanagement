package runtime.statemanager

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import runtime.common.Identifiers
import runtime.statemanager.actors.ClusterListener
import runtime.statemanager.utils.StateManagerConfig

object SmSystem extends App with StateManagerConfig with LazyLogging {
  logger.info("Starting up StateManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
