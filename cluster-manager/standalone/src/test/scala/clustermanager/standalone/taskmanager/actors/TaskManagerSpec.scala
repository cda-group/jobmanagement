package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.{ActorSpec, TestHelpers}
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import com.typesafe.config.ConfigFactory
import runtime.common.Identifiers
import runtime.protobuf.messages.{SliceUpdate, TaskManagerInit}

import scala.concurrent.duration._


object TaskManagerSpec {
  val actorSystem = ActorSystem("TaskManagerSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class TaskManagerSpec extends TestKit(TaskManagerSpec.actorSystem)
  with ImplicitSender with ActorSpec with TaskManagerConfig with TestHelpers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A TaskManager Actor" must {

    "Send SliceUpdate " in {
      // TODO: Fix
      // tm receives deadletter from probe for some damn reason...
      /*
      val probe = TestProbe()
      val tm = system.actorOf(TaskManager(), Identifiers.TASK_MANAGER)
      probe.send(tm, TaskManagerInit())
      probe.expectMsgType[SliceUpdate](10000.milliseconds)
      */
    }
  }


}
