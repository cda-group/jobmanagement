package clustermanager.yarn.taskmaster

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import runtime.protobuf.messages.ActorRefProto


/** YARN ApplicationMaster.
  *
  * Each time a new job is deployed, the TaskMasterApplication
  * is created on one of the machines in the YARN cluster.
  */
private[yarn] object TaskMasterApplication extends App with LazyLogging {
  logger.info("Starting up TaskMasterApplication")

  if (args.length <= 2) {
    logger.error("Wrong number of arguments applied, shutting down!")
    System.exit(1)
  } else {
    val appMasterRef = args(0)
    val stateMasterRef = args(1)
    val jobId = args(2)


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
    val appMaster = ActorRefProto(appMasterRef)
    val stateMaster = ActorRefProto(stateMasterRef)

    val taskmaster = system.actorOf(TaskMaster(appMaster, stateMaster, jobId), "taskmaster")
  }

}
