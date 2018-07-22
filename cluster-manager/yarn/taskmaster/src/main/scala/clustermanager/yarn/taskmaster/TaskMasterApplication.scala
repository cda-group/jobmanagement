package clustermanager.yarn.taskmaster

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import runtime.protobuf.messages.ActorRefProto


/**
  *  Jar that will be sent into the Yarn Cluster.
  *  It is responsible for talking to Yarn's RM and NM
  *  to fix containers for the tasks.
  */
private[yarn] object TaskMasterApplication extends App {

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
    implicit val system = ActorSystem("ArcRuntime", ConfigFactory.parseString(
      s"""
         | akka.actor.provider = remote
         | akka.actor.remote.enabled-transports = ["akka.remote.netty.tcp"]
         | akka.remote.netty.tcp.hostname = $localhostname
         | akka.remote.netty.tcp.port = 0
         | akka.actor.serializers.proto = "runtime.protobuf.ProtobufSerializer"
         | akka.actor.serializers.java = "akka.serialization.JavaSerializer"
         | akka.actor.serialization-bindings {"scalapb.GeneratedMessage" = proto}
    """.stripMargin))

    import runtime.protobuf.ProtoConversions.ActorRef._
    val ref = ActorRefProto(appMasterRef)
    val taskmaster = system.actorOf(TaskMaster(ref, jobId))
  }

}
