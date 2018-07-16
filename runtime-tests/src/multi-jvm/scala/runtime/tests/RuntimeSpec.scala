package runtime.tests

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import runtime.common.Identifiers

import scala.concurrent.duration._

class RuntimeSpec extends MultiNodeSpec(ClusterConfig)
with STMultiNodeSpec with ImplicitSender {


  def initialParticipants = roles.size


  val taskmanager = ClusterConfig.taskmanager
  val resourcemanager = ClusterConfig.resourcemanager
  val appmanager = ClusterConfig.appmanager
  val statemanager = ClusterConfig.statemanager

  val tmAddr = node(taskmanager).address
  val rmAddr = node(resourcemanager).address
  val amAddr = node(appmanager).address
  val smAddr = node(statemanager).address


  "RuntimeSpec" must {
    "set up runtime cluster" in within(15.seconds) {

      // Set up Listeners
      /*
      runOn(clustermanager.standalone.taskmanager) {
        system.actorOf(ClusterListener(), Identifiers.LISTENER)
      }

      runOn(clustermanager.standalone.resourcemanager) {
        system.actorOf(ClusterListener(), Identifiers.LISTENER)
      }
      */

      runOn(appmanager) {
        system.actorOf(runtime.appmanager.actors.ClusterListener(), Identifiers.LISTENER)
      }

      runOn(statemanager) {
        system.actorOf(runtime.statemanager.actors.ClusterListener(), Identifiers.LISTENER)
      }


      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      // Bootstrap
      Cluster(system) joinSeedNodes(List(tmAddr))

      // Verify nodes
      receiveN(4).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(tmAddr, rmAddr, amAddr, smAddr)
      )

      Cluster(system).unsubscribe(testActor)
    }

    testConductor.enter("all-up")
  }
}
