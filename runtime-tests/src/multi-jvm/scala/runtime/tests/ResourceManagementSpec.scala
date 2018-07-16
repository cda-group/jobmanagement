package runtime.tests

import akka.testkit.TestProbe
import runtime.common.ActorPaths
import runtime.protobuf.messages.{AllocateSuccess, NoSlotsAvailable}


class ResourceManagementSpecMultiJvmNode1 extends ResourceManagementSpec
class ResourceManagementSpecMultiJvmNode2 extends ResourceManagementSpec
class ResourceManagementSpecMultiJvmNode3 extends ResourceManagementSpec
class ResourceManagementSpecMultiJvmNode4 extends ResourceManagementSpec


class ResourceManagementSpec extends RuntimeSpec with RuntimeHelper {

  import runtime.protobuf.ProtoConversions.ActorRef._

  "Resource Management" must {

    "wait for nodes to join barrier" in {
      enterBarrier("startup")
    }


    "allocate slot successfully" in {
      runOn(resourcemanager) {
        enterBarrier("allocated_slot")
      }

      runOn(taskmanager) {
        enterBarrier("allocated_slot")
      }

      runOn(statemanager) {
        enterBarrier("allocated_slot")
      }

      runOn(appmanager) {
        val rm = system.actorSelection(ActorPaths.resourceManager(rmAddr))
        val probe = TestProbe()
        rm ! smallJob.copy(appMasterRef = Some(probe.ref))
        expectMsgType[AllocateSuccess]
        enterBarrier("allocated_slot")
      }

    }

    "handle no slot availibility" in {
      runOn(resourcemanager) {
        enterBarrier("allocated_slot_fail")
      }

      runOn(taskmanager) {
        enterBarrier("allocated_slot_fail")
      }

      runOn(appmanager) {
        val rm = system.actorSelection(ActorPaths.resourceManager(rmAddr))
        val probe = TestProbe()
        rm ! tooBigJob.copy(appMasterRef = Some(probe.ref))
        expectMsg(NoSlotsAvailable())
        enterBarrier("allocated_slot_fail")
      }

      runOn(statemanager) {
        enterBarrier("allocated_slot_fail")
      }
    }

    //TODO: No Task Manager available


    enterBarrier("finished")
  }
}
