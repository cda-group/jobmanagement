package runtime.appmanager

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import runtime.appmanager.actors.ClusterListener
import runtime.common.Identifiers
import utils.AppManagerConfig

object AmSystem extends App with AppManagerConfig with LazyLogging {
  logger.info("Starting up AppManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val appmanager = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
