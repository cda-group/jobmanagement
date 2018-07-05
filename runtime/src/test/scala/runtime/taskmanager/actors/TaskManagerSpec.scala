package runtime.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.models.{Allocate, SlotUpdate, TaskManagerInit}
import runtime.taskmanager.actors.TaskManager.TMNotInitialized
import runtime.taskmanager.utils.TaskManagerConfig

import scala.concurrent.duration._

class TaskManagerSpec extends TestKit(ActorSystem("TaskManagerSpec"))
  with ImplicitSender with ActorSpec with TaskManagerConfig {

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
      probe.send(tm, TaskManagerInit)
      probe.expectMsgType[SlotUpdate](slotTick.millis + slotTick.millis)
    }
  }


}
