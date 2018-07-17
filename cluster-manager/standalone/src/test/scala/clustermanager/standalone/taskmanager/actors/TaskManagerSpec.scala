package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.TestHelpers
import clustermanager.standalone.taskmanager.actors.TaskManager.TMNotInitialized
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import com.typesafe.config.ConfigFactory
import runtime.common.ActorSpec
import runtime.protobuf.messages.{Allocate, SlotUpdate, TaskManagerInit}

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

    "Fail allocation before being initialized" in {
      val tm = system.actorOf(TaskManager())
      tm ! Allocate(testArcJob, Seq())
      expectMsg(TMNotInitialized)
    }

    "Send SlotUpdate" in {
      val probe = TestProbe()
      val tm = system.actorOf(TaskManager())
      probe.send(tm, TaskManagerInit())
      probe.expectMsgType[SlotUpdate](slotTick.millis + slotTick.millis)
    }
  }


}
