package runtime.resourcemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.models.{NoSlotsAvailable, NoTaskManagerAvailable}
import runtime.resourcemanager.actors.ClusterListener.TaskManagerRegistration
import runtime.resourcemanager.actors.ResourceManager.SlotRequest

class SlotManagerSpec extends TestKit(ActorSystem("SlotManagerSpec"))
  with ImplicitSender with ActorSpec {

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
