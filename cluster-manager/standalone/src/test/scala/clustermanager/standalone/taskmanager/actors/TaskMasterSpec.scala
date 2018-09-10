package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import clustermanager.standalone.{ActorSpec, TestHelpers}
import com.typesafe.config.ConfigFactory
import runtime.protobuf.messages.{Container, TaskTransferConn, TasksCompiled}


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

    /*
    "Receive Transfer Channel" in {
      val container = Container()
      val tm = system.actorOf(TaskMaster(container))
      tm ! TasksCompiled()
      val conn = expectMsgType[TaskTransferConn]
    }
    */
  }

}
