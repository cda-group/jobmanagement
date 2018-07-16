package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import runtime.appmanager.TestHelpers
import runtime.appmanager.actors.AppManager.{ArcJobRequest, ResourceManagerUnavailable}
import runtime.common.ActorSpec

object AppManagerSpec {
  val actorSystem = ActorSystem("AppManagerSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class AppManagerSpec extends TestKit(AppManagerSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "An AppManager Actor" must {

    "handle no available ResourceManager" in {
      val appManager = system.actorOf(ArcAppManager())
      appManager ! ArcJobRequest(testArcJob)
      expectMsg(ResourceManagerUnavailable)
    }

    "handle Job Request" in {
      //TODO
    }
  }
}
