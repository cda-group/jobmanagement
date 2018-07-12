package runtime.statemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.messages.{StateManagerJob, StateMasterConn}

class StateManagerSpec extends TestKit(ActorSystem("StateManagerSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A StateManager Actor" must {

    "Create StateMaster on request" in {
      val appMaster = TestProbe()
      val probe = TestProbe()
      val manager = system.actorOf(StateManager())
      import runtime.common.messages.ProtoConversions.ActorRef._
      probe.send(manager, StateManagerJob(appMaster.ref))
      probe.expectMsgType[StateMasterConn]
    }

  }

}
