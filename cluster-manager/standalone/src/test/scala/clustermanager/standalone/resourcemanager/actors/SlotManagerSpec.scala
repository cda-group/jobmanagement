package clustermanager.standalone.resourcemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.{ActorSpec, TestHelpers}
import clustermanager.standalone.resourcemanager.actors.ClusterListener.TaskManagerRegistration
import clustermanager.standalone.resourcemanager.actors.ResourceManager.SlotRequest
import com.typesafe.config.ConfigFactory
import runtime.protobuf.messages.{NoSlotsAvailable, NoTaskManagerAvailable}


object SlotManagerSpec {
  val actorSystem = ActorSystem("SlotManagerSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class SlotManagerSpec extends TestKit(SlotManagerSpec.actorSystem)
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
