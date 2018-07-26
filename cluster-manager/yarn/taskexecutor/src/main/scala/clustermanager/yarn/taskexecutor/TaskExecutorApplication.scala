package clustermanager.yarn.taskexecutor

import java.nio.file.Paths

import akka.actor.ActorSystem
import clustermanager.common.executor.ExecutionEnvironment
import clustermanager.yarn.client._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import kamon.sigar.SigarProvisioner
import runtime.common.Identifiers
import runtime.protobuf.messages.{ActorRefProto, ArcTask}

private[yarn] object TaskExecutorApplication extends App with LazyLogging {
  logger.info("TaskExecutorApplication starting up")

  if (args.length > 3) {
    logger.info("args: " + args)
    val hdfsPath = args(0)
    val jobId = args(1)
    val taskId = args(2).toInt
    val taskMaster = args(3)
    val appMaster = args(4)
    val stateMaster = args(5)

    // Set up execution environment on the local filesystem
    val env = new ExecutionEnvironment(jobId)
    env.create()

    val binName = Paths.get(hdfsPath)
      .getFileName
      .toString

    val binPath = env.getJobPath + "/" + binName

    if (!YarnUtils.moveToLocal(hdfsPath, binPath)) {
      logger.error("Could not move the binary to the execution environment, shutting down!")
      System.exit(1)
    }

    // TODO: make sure this works on all platforms including "Windows"
    env.setAsExecutable(binPath)

    // Makes sure it is loaded.
    loadSigar()

    val localhostname = java.net.InetAddress
      .getLocalHost
      .getHostAddress

    // Set up an ActorSystem that uses Remoting
    implicit val system = ActorSystem(Identifiers.CLUSTER, ConfigFactory.parseString(
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
    val appMasterProto = ActorRefProto(appMaster)
    val taskMasterProto = ActorRefProto(taskMaster)
    val stateMasterProto = ActorRefProto(stateMaster)
    val taskexecutor = system.actorOf(TaskExecutor(binPath, taskId,
      appMasterProto, taskMasterProto, stateMasterProto), "taskexecutor")

    system.whenTerminated
  } else {
    logger.error("Args are 1. HDFS binpath, 2. jobId, 3 appMaster Ref, 4. stateMaster ref")
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
