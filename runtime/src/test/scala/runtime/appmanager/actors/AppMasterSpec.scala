package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import runtime.ActorSpec

class AppMasterSpec extends TestKit(ActorSystem("AppMasterSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "An AppMaster Actor" must {
    //TODO
  }

}
