package runtime.tests.yarn

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import runtime.common.Identifiers
import runtime.tests.STMultiNodeSpec
import scala.concurrent.duration._

/** RuntimeSpec sets up our runtime cluster,
  * in this case using YARN as cluster manager.
  */
class RuntimeSpec extends MultiNodeSpec(ClusterConfig)
  with STMultiNodeSpec with ImplicitSender {

  def initialParticipants = roles.size

  val appmanager = ClusterConfig.appmanager
  val statemanager = ClusterConfig.statemanager

  val amAddr = node(appmanager).address
  val smAddr = node(statemanager).address


  "RuntimeSpec" must {
    "set up runtime cluster for Yarn" in within(15.seconds) {

      // Set up Listeners
      runOn(appmanager) {
        system.actorOf(runtime.appmanager.actors.ClusterListener(), Identifiers.LISTENER)
      }

      runOn(statemanager) {
        system.actorOf(runtime.statemanager.actors.ClusterListener(), Identifiers.LISTENER)
      }

      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      // Bootstrap
      Cluster(system) joinSeedNodes(List(amAddr))

      // Verify nodes
      receiveN(2).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(amAddr, smAddr)
      )

      Cluster(system).unsubscribe(testActor)
    }

    testConductor.enter("all-up")
  }
}
