package runtime

import akka.actor.Address
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
  val driver = ClusterConfig.driver

  val tmAddr = node(taskmanager).address
  val rmAddr = node(resourcemanager).address
  val driverAddr = node(driver).address


  "RuntimeSpec" must {
    "set up runtime cluster" in within(15.seconds) {

      // Set up Listeners
      runOn(taskmanager) {
        println("Setting up TM")
        import runtime.taskmanager.actors.ClusterListener
        system.actorOf(ClusterListener(), Identifiers.LISTENER)
      }

      runOn(resourcemanager) {
        println("Setting up RM")
        import runtime.resourcemanager.actors.ClusterListener
        system.actorOf(ClusterListener(), Identifiers.LISTENER)
      }

      runOn(driver) {
        println("Setting up Driver")
        import runtime.driver.actors.ClusterListener
        system.actorOf(ClusterListener(), Identifiers.LISTENER)
      }

      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      // Bootstrap
      Cluster(system) joinSeedNodes(List(tmAddr))

      // Verify nodes
      receiveN(3).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(tmAddr, rmAddr, driverAddr)
      )

      Cluster(system).unsubscribe(testActor)
    }

    testConductor.enter("all-up")
  }
}
