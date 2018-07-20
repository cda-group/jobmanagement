package runtime.taskmaster.yarn

import akka.actor.ActorSystem
import clustermanager.yarn.Client
import com.typesafe.config.ConfigFactory
import runtime.protobuf.ExternalAddress
import runtime.protobuf.messages.ActorRefProto


/**
  *  Jar that will be sent into the Yarn Cluster.
  *  It is responsible for talking to Yarn's RM and NM
  *  to fix containers for the tasks.
  */
object TaskMasterApplication extends App {

  if (args.length <= 1) {
    println("NO REF ARG APPLIED")
    System.exit(1)
  } else {
    val appMasterRef = args(0)
    val jobId = args(1)


    val localhostname = java.net.InetAddress
      .getLocalHost
      .getHostAddress

    // Set up an ActorSystem that uses Remoting, as the TaskMaster for YARN
    // will not be a member of the Akka Cluster
    implicit val system = ActorSystem("taskmaster", ConfigFactory.parseString(
      s"""
         | akka.actor.provider = remote
         | akka.actor.remote.enabled-transports = ["akka.remote.netty.tcp"]
         | akka.actor.remote.netty.tcp.hostname = $localhostname
         | akka.actor.remote.netty.tcp.port = 3000
         | akka.actor.serializers.proto = "runtime.protobuf.ProtobufSerializer"
         | akka.actor.serializers.java = "akka.serialization.JavaSerializer"
         | akka.actor.serialization-bindings {"scalapb.GeneratedMessage" = proto}
    """.stripMargin))

    println(localhostname)

    import runtime.protobuf.ProtoConversions.ActorRef._
    val pr = ActorRefProto(appMasterRef)
    val taskmaster = system.actorOf(TaskMaster(pr, jobId))
  }

}
