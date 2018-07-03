package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.{BinariesCompiled, BinaryTransferComplete, BinaryTransferConn}
import runtime.taskmanager.actors.BinaryManager

class BinarySenderSpec extends TestKit(ActorSystem("BinarySenderSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A BinarySender Actor" must {

    "complete transfer to BinaryReceiver" in {
      val jm = TestProbe()
      val bm = system.actorOf(BinaryManager(testArcJob, Seq(), jm.ref))
      bm ! BinariesCompiled
      val conn = expectMsgType[BinaryTransferConn]
      val bmProbe = TestProbe()
      val binarySender = system.actorOf(BinarySender(conn.inet, "test".getBytes(), bmProbe.ref))
      bmProbe.expectMsgType[BinaryTransferComplete]
    }
  }

}
