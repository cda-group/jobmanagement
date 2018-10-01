package clustermanager.standalone.resourcemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import clustermanager.standalone.{ActorSpec, TestHelpers}
import com.typesafe.config.ConfigFactory


object SliceManagerSpec {
  val actorSystem = ActorSystem("SliceManagerSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class SliceManagerSpec extends TestKit(SliceManagerSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  /*
  "A SliceManager Actor" must {

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
  */


}
