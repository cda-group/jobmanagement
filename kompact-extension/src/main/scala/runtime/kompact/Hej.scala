package runtime.kompact


import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory


object Hej extends App {
  val system = ActorSystem("test", ConfigFactory.load())
  val sampleActor = system.actorOf(Props(new SampleActor), "sampleActor")
}
