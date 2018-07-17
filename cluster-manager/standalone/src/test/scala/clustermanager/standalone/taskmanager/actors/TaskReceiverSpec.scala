package clustermanager.standalone.taskmanager.actors

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.Tcp.{Bind, Bound, Connected, Register}
import akka.io.{IO, Tcp}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import clustermanager.standalone.TaskSender
import clustermanager.standalone.taskmanager.actors.TaskMaster.{TaskReady, TaskUploaded}
import clustermanager.standalone.taskmanager.utils.ExecutionEnvironment
import com.typesafe.config.ConfigFactory
import runtime.common.ActorSpec
import runtime.protobuf.messages.TaskTransferComplete

import scala.concurrent.Await
import scala.concurrent.duration._

object TaskReceiverSpec {
  val actorSystem = ActorSystem("TaskReceiverSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class TaskReceiverSpec extends TestKit(TaskReceiverSpec.actorSystem)
  with ImplicitSender with ActorSpec {

  private val env = new ExecutionEnvironment("test-recv")


  override def afterAll {
    TestKit.shutdownActorSystem(system)
    env.clean()
  }


  "A TaskReceiver Actor" must {

    "Write received binary to file" in {
      env.create()
      // Set up TaskReceiver
      val tr = system.actorOf(TaskReceiver("1", env), "rec")

      val probe = TestProbe()
      implicit val timeout = Timeout(3 seconds)

      // Set up IO Connection
      val f = IO(Tcp) ? Bind(probe.ref, new InetSocketAddress("localhost", 0))
      val bound = Await.result(f, 3 seconds)
      val inet = bound match {case Bound(i) => i}

      // Set up TaskSender
      val tmProbe = TestProbe()
      val ts = system.actorOf(TaskSender(inet, "test".getBytes(), tmProbe.ref))
      val connected = probe.expectMsgType[Connected]
      ts ! Register(tr)

      // Assert
      tmProbe.expectMsgType[TaskTransferComplete]
      tmProbe.send(tr, TaskUploaded)
      tmProbe.expectMsgType[TaskReady]
    }
  }

}
