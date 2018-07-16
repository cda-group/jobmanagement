package clustermanager.standalone.resourcemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import ClusterListener.TaskManagerRegistration
import ResourceManager.SlotRequest
import clustermanager.standalone.resourcemanager.TestHelpers
import runtime.common.ActorSpec
import runtime.protobuf.messages.{NoSlotsAvailable, NoTaskManagerAvailable}

class SlotManagerSpec extends TestKit(ActorSystem("SlotManagerSpec"))
  with ImplicitSender with ActorSpec with TestHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A SlotManager Actor" must {

    "Handle no available task managers" in {
      val sm = system.actorOf(SlotManager())
      sm ! SlotRequest(testArcJob)
      expectMsg(NoTaskManagerAvailable())
    }

    //TODO: improve
    "Handle no available slots" in {
      val tmProbe = TestProbe()
      val sm = system.actorOf(SlotManager())
      sm ! TaskManagerRegistration(tmProbe.ref.path.address)
      sm ! SlotRequest(testArcJob)
      expectMsg(NoSlotsAvailable())
    }
  }


}
