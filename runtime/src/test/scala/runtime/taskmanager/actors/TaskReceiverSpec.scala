package runtime.taskmanager.actors

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Bind, Bound, Connected, Register}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import runtime.ActorSpec
import runtime.appmanager.actors.TaskSender
import runtime.common.models.TaskTransferComplete
import runtime.taskmanager.actors.TaskMaster.{TaskReady, TaskUploaded}
import runtime.taskmanager.utils.ExecutionEnvironment

import scala.concurrent.Await
import scala.concurrent.duration._

class TaskReceiverSpec extends TestKit(ActorSystem("TaskReceiverSpec"))
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
