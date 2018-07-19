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

  if (args.length <= 0) {
    println("NO REF ARG APPLIED")
    System.exit(1)
  } else {
    val ref = args(0)


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
    """.stripMargin))

    println(localhostname)

    import runtime.protobuf.ProtoConversions.ActorRef._
    val pr = ActorRefProto(ref)
    val taskmaster = system.actorOf(TaskMaster(pr))
  }

}
