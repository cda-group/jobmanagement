package clustermanager.yarn.taskexecutor

import akka.actor.ActorSystem
import clustermanager.common.executor.ExecutionEnvironment
import clustermanager.yarn.utils.YarnUtils
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import kamon.sigar.SigarProvisioner
import runtime.protobuf.messages.{ActorRefProto, ArcTask}

private[yarn] object TaskExecutorApplication extends App with LazyLogging {
  logger.info("TaskExecutorApp started")

  if (args.length > 1) {
    val binPath = args(0)
    val e = new ExecutionEnvironment("id")
    YarnUtils.moveToLocal(binPath, "/home/meldrum/my_binary")
    e.setAsExecutable("/home/meldrum/my_binary")
    val taskMaster = args(1)
    val stateMaster = args(2)

    // Makes sure it is loaded.
    loadSigar()

    val localhostname = java.net.InetAddress
      .getLocalHost
      .getHostAddress

    // Set up an ActorSystem that uses Remoting
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
    val taskmasterProto = ActorRefProto(taskMaster)
    val statemasterProto = ActorRefProto(stateMaster)
    val conf = TaskExecutorConf(1000)
    val task = ArcTask()
    val taskexecutor = system.actorOf(TaskExecutor("/home/meldrum/my_binary", task, taskmasterProto, statemasterProto, conf))
  } else {
    logger.error("Args are 1. Binpath, 2. taskMaster ref, 3. stateMaster ref")
  }


  private def loadSigar(): Unit = {
    try {
      if (!SigarProvisioner.isNativeLoaded)
        SigarProvisioner.provision()
    } catch {
      case err: Exception =>
        logger.error("Could not initialize Sigar...")
    }
  }
}
