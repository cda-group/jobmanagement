package runtime.appmanager


import actors.ClusterListener
import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.Identifiers

object AmSystem extends App with AppManagerConfig with LazyLogging {
  logger.info("Starting up AppManager")
  val system = ActorSystem(Identifiers.CLUSTER, config)
  val appmanager = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
