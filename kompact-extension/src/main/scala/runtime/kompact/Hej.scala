package runtime.kompact


import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory


object Hej extends App {
  val system = ActorSystem("test", ConfigFactory.parseString(
    """
      | akka.kompact.port = 2020
      | akka.kompact.host = localhost
      | akka.extensions = ["runtime.kompact.KompactExtension"]
    """.stripMargin))

  val sampleActor = system.actorOf(Props(new SampleActor), "sampleActor")
}
