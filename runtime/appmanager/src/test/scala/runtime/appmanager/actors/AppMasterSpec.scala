package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import runtime.common.ActorSpec

object AppMasterSpec {
  val actorSystem = ActorSystem("AppMasterSpec", ConfigFactory.parseString(
    """
      | akka.actor.provider = cluster
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class AppMasterSpec extends TestKit(AppMasterSpec.actorSystem)
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "An AppMaster Actor" must {
    //TODO
  }

}
