package runtime.appmanager


import actors.ClusterListener
import akka.actor.ActorSystem
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.Identifiers

object AmSystem extends App with AppManagerConfig {
  val system = ActorSystem("JmCluster", config)
  val appmanager = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
