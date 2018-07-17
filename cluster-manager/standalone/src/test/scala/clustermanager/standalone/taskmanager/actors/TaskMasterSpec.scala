package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.TestHelpers
import com.typesafe.config.ConfigFactory
import runtime.common.ActorSpec
import runtime.protobuf.messages.{TaskTransferConn, TasksCompiled}


object TaskMasterSpec {
  val actorSystem = ActorSystem("TaskMasterSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class TaskMasterSpec extends TestKit(TaskMasterSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestHelpers {

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
