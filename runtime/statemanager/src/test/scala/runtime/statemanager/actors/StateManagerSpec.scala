package runtime.statemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import runtime.protobuf.messages.{StateManagerJob, StateMasterConn}
import runtime.statemanager.ActorSpec


object StateManagerSpec {
  val actorSystem = ActorSystem("StateManagerSpec", ConfigFactory.parseString(
    """
      | akka.actor.provider = cluster
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class StateManagerSpec extends TestKit(StateManagerSpec.actorSystem)
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A StateManager Actor" must {

    "Create StateMaster on request" in {
      val appMaster = TestProbe()
      val probe = TestProbe()
      val manager = system.actorOf(StateManager())
      import runtime.protobuf.ProtoConversions.ActorRef._
      probe.send(manager, StateManagerJob(appMaster.ref))
      probe.expectMsgType[StateMasterConn]
    }

  }

}
