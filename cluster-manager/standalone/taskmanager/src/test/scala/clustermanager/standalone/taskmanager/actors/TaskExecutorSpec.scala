package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.ActorSpec
import runtime.protobuf.messages.ArcTask

import scala.concurrent.duration._

class TaskExecutorSpec extends TestKit(ActorSystem("TaskExecutorSpec"))
  with ImplicitSender with ActorSpec with TaskManagerConfig {

  // UNIX bias
  private final val program = "ls"

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "A TaskExecutor Actor" must {

    "Terminate after execution" in {
      val am = TestProbe()
      val sm = TestProbe()
      val be = system.actorOf(TaskExecutor(program, ArcTask("", "", ""), am.ref, sm.ref))
      val probe = TestProbe()
      probe watch be
      probe.expectTerminated(be, taskExecutorHealthCheck.millis + taskExecutorHealthCheck.millis)
    }
  }

}
