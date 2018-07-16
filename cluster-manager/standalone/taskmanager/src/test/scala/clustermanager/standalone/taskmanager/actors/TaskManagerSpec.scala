package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.taskmanager.TestHelpers
import clustermanager.standalone.taskmanager.actors.TaskManager.TMNotInitialized
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.ActorSpec
import runtime.protobuf.messages.{Allocate, SlotUpdate, TaskManagerInit}

import scala.concurrent.duration._

class TaskManagerSpec extends TestKit(ActorSystem("TaskManagerSpec"))
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
