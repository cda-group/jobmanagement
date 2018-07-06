package runtime.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.messages.{TaskTransferConn, TasksCompiled}

class TaskMasterSpec extends TestKit(ActorSystem("TaskMasterSpec"))
  with ImplicitSender with ActorSpec {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A TaskMaster Actor" must {

    "Receive Transfer Channel" in {
      val jm = TestProbe()
      val tm = system.actorOf(TaskMaster(testArcJob, Seq(), jm.ref))
      tm ! TasksCompiled()
      val conn = expectMsgType[TaskTransferConn]
    }
  }

}
