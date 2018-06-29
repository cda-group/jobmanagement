package runtime.driver.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import runtime.ActorSpec

class JobManagerSpec extends TestKit(ActorSystem("JobManagerSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A JobManager Actor" must {
    //TODO
  }

}
