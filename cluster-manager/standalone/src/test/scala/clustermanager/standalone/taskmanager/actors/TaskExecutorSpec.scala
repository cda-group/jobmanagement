package clustermanager.standalone.taskmanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import clustermanager.common.executor.ExecutionEnvironment
import clustermanager.standalone.ActorSpec
import clustermanager.standalone.taskmanager.actors.TaskExecutor.ExecutorInit
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import com.typesafe.config.ConfigFactory
import runtime.protobuf.messages.{ArcTask, StateMasterConn}

import scala.concurrent.duration._

object TaskExecutorSpec {
  val actorSystem = ActorSystem("TaskExecutorSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class TaskExecutorSpec extends TestKit(TaskExecutorSpec.actorSystem)
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
      val env = new ExecutionEnvironment("test")
      val sMasterConn = StateMasterConn(null, "")
      val init = ExecutorInit(env, ArcTask("", 1, 1024, "", ""), am.ref, sMasterConn)
      val be = system.actorOf(TaskExecutor(init))
      val probe = TestProbe()
      probe watch be
      probe.expectTerminated(be, taskExecutorHealthCheck.millis + taskExecutorHealthCheck.millis)
    }
  }

}
