package runtime.statemanager

import akka.actor.ActorSystem
import runtime.common.Identifiers
import runtime.statemanager.actors.ClusterListener
import runtime.statemanager.utils.StateManagerConfig

object SmSystem extends App with StateManagerConfig{
  val system = ActorSystem("JmCluster", config)
  val handler = system.actorOf(ClusterListener(), Identifiers.LISTENER)

  system.whenTerminated
}
