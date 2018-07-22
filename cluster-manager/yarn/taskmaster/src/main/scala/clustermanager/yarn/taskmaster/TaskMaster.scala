package clustermanager.yarn.taskmaster

import java.nio.ByteBuffer
import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import clustermanager.yarn.utils.YarnTaskExecutor
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest
import org.apache.hadoop.yarn.client.api.async.{AMRMClientAsync, NMClientAsync}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.Records
import runtime.protobuf.messages.{ArcProfile, YarnTaskMasterUp, YarnTaskTransferred}

import scala.concurrent.duration._


private[yarn] object TaskMaster {
  def apply(appmaster: ActorRef, jobId: String): Props =
    Props(new TaskMaster(appmaster, jobId))
}

/**
  * The TaskMaster for Yarn will act as Yarn's ApplicationMaster
  */
private[yarn] class TaskMaster(appmaster: ActorRef, jobId: String) extends Actor with ActorLogging {

  private var rmClient: AMRMClientAsync[ContainerRequest] = _
  private var nmClient: NMClientAsync = _
  private val conf = new YarnConfiguration()

  // Available resources from the ResourceManager
  private var maxMemory = None: Option[Long]
  private var maxCores = None: Option[Int]

  import runtime.protobuf.ProtoConversions.ActorRef._
  implicit val sys = context.system

  var bin = ""

  implicit val timeout = Timeout(2 seconds)

  override def preStart(): Unit = {
    initYarnClients()
    appmaster ! YarnTaskMasterUp(self)
  }

  def receive = {
    case YarnTaskTransferred(binPath, profile) =>
      log.info("Bin has been uploaded to: " + binPath)
      // Request container allocation for the binary and on allocation,
      // start an TaskExecutor with the binary as local resource?
      bin = binPath
      allocateContainer(profile)
    case _ =>
  }

  private def initYarnClients(): Unit = {
    val heartbeatInterval = 1000
    rmClient = AMRMClientAsync.createAMRMClientAsync[ContainerRequest](heartbeatInterval, AMRMHandler)
    rmClient.init(conf)
    rmClient.start()

    log.info("Starting RM client")

    nmClient = NMClientAsync.createNMClientAsync(NMHandler)
    nmClient.init(conf)
    nmClient.start()

    log.info("Starting NM client")


    log.info("Registering ApplicationMaster")
    val res = rmClient.registerApplicationMaster("somename", 0, "")
    val mem = res.getMaximumResourceCapability.getMemorySize
    val cpu = res.getMaximumResourceCapability.getVirtualCores
    log.info(s"TaskMaster currently has $mem memory and $cpu cores available")

    maxCores = Some(cpu)
    maxMemory = Some(mem)
  }


  private def allocateContainer(profile: ArcProfile): Unit = {
    val reqMem = profile.memoryInMb
    val reqCores = profile.cpuCores

    if (maxMemory.isDefined && maxCores.isDefined) {
      if (profile.cpuCores <= maxCores.get && profile.memoryInMb <= maxMemory.get) {
        val resource = Resource.newInstance(reqMem, reqCores.toInt)
        val priority = Records.newRecord(classOf[Priority])
        // hardcoded for now
        priority.setPriority(1)

        log.info("Requesting container")
        rmClient.addContainerRequest(new ContainerRequest(resource, null, null, priority))
      }
    }
  }

  def shutdown(s: FinalApplicationStatus, message: String): Unit = {
    rmClient.unregisterApplicationMaster(s, message, null)
    rmClient.stop()
    nmClient.stop()
  }


  // AMRM Client Async Callbacks

  import scala.collection.JavaConverters._

  private def AMRMHandler: AMRMClientAsync.AbstractCallbackHandler =
    new AMRMClientAsync.AbstractCallbackHandler {
    override def onContainersAllocated(list: util.List[Container]): Unit = {
      log.info("Container allocated: " + list.asScala)
      val addr = appmaster.path.address
      val appmaterStr = appmaster.path.toStringWithAddress(addr)
      val ctx = YarnTaskExecutor.context(appmaterStr, bin)

      list.asScala.foreach { c =>
        log.info("Starting Container")
        nmClient.startContainerAsync(c, ctx)
      }
    }
    override def onContainersCompleted(list: util.List[ContainerStatus]): Unit =
      log.info(s"Containers completed $list")
    override def onContainersUpdated(list: util.List[UpdatedContainer]): Unit =
      log.info("On containers updated: $list")
    override def onShutdownRequest(): Unit =
      log.info("Request shutdown was called")
    override def getProgress: Float = 100
    override def onNodesUpdated(list: util.List[NodeReport]): Unit = {}
    override def onError(throwable: Throwable): Unit =
      log.info("Error: " + throwable.toString)
  }

  // NM Async client Callbacks

  private def NMHandler: NMClientAsync.AbstractCallbackHandler = new NMClientAsync.AbstractCallbackHandler {
    override def onGetContainerStatusError(containerId: ContainerId, throwable: Throwable): Unit =
      log.error(s"Container StatusError: $containerId with ${throwable.toString}")
    override def onContainerStatusReceived(containerId: ContainerId, containerStatus: ContainerStatus): Unit =
      log.info(s"Container status received for $containerId with $containerStatus")
    override def onContainerResourceIncreased(containerId: ContainerId, resource: Resource): Unit =
      log.info("Container resoruce increased")
    override def onStopContainerError(containerId: ContainerId, throwable: Throwable): Unit = {
      log.error(s"Error container stopped: $containerId with reason ${throwable.toString}")
    }
    override def onContainerStopped(containerId: ContainerId): Unit = {
      log.error(s"Container stopped $containerId")
    }
    override def onIncreaseContainerResourceError(containerId: ContainerId, throwable: Throwable): Unit = {}
    override def onStartContainerError(containerId: ContainerId, throwable: Throwable): Unit = {
      log.error(s"On Start Container error for $containerId with ${throwable.toString}")
    }
    override def onUpdateContainerResourceError(containerId: ContainerId, throwable: Throwable): Unit =
      log.error(s"On Update resource error for $containerId with ${throwable.toString}")

    override def onContainerStarted(containerId: ContainerId, map: util.Map[String, ByteBuffer]): Unit = {
      log.info(s"Container $containerId started with $map")
    }
    override def onContainerResourceUpdated(containerId: ContainerId, resource: Resource): Unit =
      log.info("container resource updated")
  }


}
