package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import runtime.ActorSpec
import runtime.common.ArcJobRequest
import runtime.appmanager.actors.AppManager.ResourceManagerUnavailable

class AppManagerSpec extends TestKit(ActorSystem("AppManagerSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "An AppManager Actor" must {

    "handle no available ResourceManager" in {
      val appManager = system.actorOf(AppManager())
      appManager ! ArcJobRequest(testArcJob)
      expectMsg(ResourceManagerUnavailable)
    }

    "handle Job Request" in {
      //TODO
    }
  }
}
