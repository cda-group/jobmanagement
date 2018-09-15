package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import runtime.appmanager.{ActorSpec, TestHelpers}
import runtime.appmanager.actors.AppManager.{ArcAppRequest, ResourceManagerUnavailable}

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
      val appManager = system.actorOf(StandaloneAppManager())
      val requester = TestProbe()
      requester.send(appManager, ArcAppRequest(testArcApp))
      requester.expectMsg("No StateManagers available")
    }

  }
}
