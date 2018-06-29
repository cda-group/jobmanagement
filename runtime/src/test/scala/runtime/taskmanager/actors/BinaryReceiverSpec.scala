package runtime.taskmanager.actors

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Bind, Bound, Connected, Register}
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import runtime.ActorSpec
import runtime.common.{BinariesCompiled, BinaryTransferComplete, BinaryTransferConn}
import runtime.driver.actors.BinarySender
import runtime.taskmanager.actors.BinaryManager.{BinaryReady, BinaryUploaded, BinaryWriteFailure}
import runtime.taskmanager.utils.ExecutionEnvironment

import scala.concurrent.Await
import scala.concurrent.duration._

class BinaryReceiverSpec extends TestKit(ActorSystem("BinaryReceiverSpec"))
  with ImplicitSender with ActorSpec {

  private val env = new ExecutionEnvironment("test-recv")


  override def afterAll {
    TestKit.shutdownActorSystem(system)
    env.clean()
  }


  "A BinaryReceiver Actor" must {

    "Write received binary to file" in {
      env.create()
      // Set up BinaryReceiver
      val br = system.actorOf(BinaryReceiver("1", env), "rec")

      val probe = TestProbe()
      implicit val timeout = Timeout(3 seconds)

      // Set up IO Connection
      val f = IO(Tcp) ? Bind(probe.ref, new InetSocketAddress("localhost", 0))
      val bound = Await.result(f, 3 seconds)
      val inet = bound match {case Bound(i) => i}

      // Set up BinarySender
      val bmProbe = TestProbe()
      val bs = system.actorOf(BinarySender(inet, "test".getBytes(), bmProbe.ref))
      val connected = probe.expectMsgType[Connected]
      bs ! Register(br)

      // Assert
      bmProbe.expectMsgType[BinaryTransferComplete]
      bmProbe.send(br, BinaryUploaded)
      bmProbe.expectMsgType[BinaryReady]
    }
  }

}
