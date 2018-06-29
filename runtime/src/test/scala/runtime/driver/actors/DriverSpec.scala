package runtime.driver.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import runtime.ActorSpec
import runtime.common.ArcJobRequest
import runtime.driver.actors.Driver.ResourceManagerUnavailable

class DriverSpec extends TestKit(ActorSystem("DriverSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A Driver Actor" must {

    "handle no available ResourceManager" in {
      val driver = system.actorOf(Driver())
      driver ! ArcJobRequest(testArcJob)
      expectMsg(ResourceManagerUnavailable)
    }

    "handle Job Request" in {
      //TODO
    }
  }
}
