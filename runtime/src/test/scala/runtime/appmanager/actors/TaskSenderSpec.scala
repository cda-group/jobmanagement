package runtime.appmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common._
import runtime.common.models.{TaskTransferComplete, TaskTransferConn, TasksCompiled}
import runtime.taskmanager.actors.TaskMaster

class TaskSenderSpec extends TestKit(ActorSystem("TaskSenderSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A TaskSender Actor" must {

    "complete transfer to TaskReceiver" in {
      val jm = TestProbe()
      val tm = system.actorOf(TaskMaster(testArcJob, Seq(), jm.ref))
      tm ! TasksCompiled()
      val conn = expectMsgType[TaskTransferConn]
      val tmProbe = TestProbe()
      import ProtoConversions.InetAddr._
      val taskSender = system.actorOf(TaskSender(conn.inet, "test".getBytes(), tmProbe.ref))
      tmProbe.expectMsgType[TaskTransferComplete]
    }
  }

}
