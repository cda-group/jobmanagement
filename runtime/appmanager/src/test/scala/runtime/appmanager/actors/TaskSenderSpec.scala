package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import runtime.appmanager.TestHelpers
import runtime.common.ActorSpec

object TaskSenderSpec {
  val actorSystem = ActorSystem("TaskSenderSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}


class TaskSenderSpec extends TestKit(TaskSenderSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  import runtime.protobuf.ProtoConversions.InetAddr._

  "A TaskSender Actor" must {

    "complete transfer to TaskReceiver" in {
      /*
      val jm = TestProbe()
      val tm = system.actorOf(TaskMaster(testArcJob, Seq(), jm.ref))
      tm ! TasksCompiled()
      val conn = expectMsgType[TaskTransferConn]
      val tmProbe = TestProbe()
      val taskSender = system.actorOf(TaskSender(conn.inet, "test".getBytes(), tmProbe.ref))
      tmProbe.expectMsgType[TaskTransferComplete]
      */
    }
  }

}
