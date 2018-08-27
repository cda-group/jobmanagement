package runtime.kompact

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import runtime.kompact.KompactAsk.AskSuccess
import runtime.kompact.messages.{Hello, KompactAkkaMsg}

import scala.concurrent.{ExecutionContext, Future}

object ExtensionSpec {
  val actorSystem = ActorSystem("ExtensionSpec", ConfigFactory.parseString(
    """
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
      | akka.extensions = ["runtime.kompact.KompactExtension"]
      | akka.kompact.port = 1337
      | akka.kompact.host = "localhost"
      | akka.log-dead-letters-during-shutdown = false
      | akka.log-dead-letters = 0
    """.stripMargin))

}

class ExtensionSpec extends TestKit(ExtensionSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestSettings {

  implicit val ec: ExecutionContext = system.dispatcher

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "KompactExtension" must {

    "Register executor, perform ask request and handle DeathWatch" in {
      // TODO: TestClient must be rewired

      /*
      val extensionActor = system.actorOf(ExtensionActor(self), extensionActorName)
      val port = system.settings.config.getInt("akka.kompact.port")
      val host = system.settings.config.getString("akka.kompact.host")
      val simpleClient = new SimpleClient()
      Future(simpleClient.run(host, port))
      // We should receive an ExecutorUp msg when a client connects
      expectMsgType[ExecutorUp]

      import ExtensionActor._
      val msg = KompactAkkaMsg().withHello(Hello("ask"))
      extensionActor ! TestAsk(msg)
      expectMsgType[AskSuccess]

      extensionActor ! TestDeathWatch
      expectMsgType[ExecutorTerminated]
      */
    }
  }

}
