package runtime.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.{BinariesCompiled, BinaryTransferConn}

class BinaryManagerSpec extends TestKit(ActorSystem("BinaryManagerSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A BinaryManager Actor" must {

    "Receive Transfer Channel" in {
      val jm = TestProbe()
      val bm = system.actorOf(BinaryManager(testArcJob, Seq(), jm.ref))
      bm ! BinariesCompiled
      val conn = expectMsgType[BinaryTransferConn]
    }
  }

}
