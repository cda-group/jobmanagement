package clustermanager.yarn.taskmaster

import java.nio.ByteBuffer
import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import clustermanager.yarn.utils.YarnTaskExecutor
import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest
import org.apache.hadoop.yarn.client.api.async.{AMRMClientAsync, NMClientAsync}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.Records
import runtime.protobuf.ExternalAddress
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._


private[yarn] object TaskMaster {
  def apply(appmaster: ActorRef, statemaster: ActorRef, jobId: String): Props =
    Props(new TaskMaster(appmaster, statemaster, jobId))
}

/**
  * The TaskMaster for Yarn will act as Yarn's ApplicationMaster
  */
private[yarn] class TaskMaster(appmaster: ActorRef, statemaster: ActorRef, jobId: String)
  extends Actor with ActorLogging {

  private var rmClient: AMRMClientAsync[ContainerRequest] = _
  private var nmClient: NMClientAsync = _
  private val conf = new YarnConfiguration()

  // Available resources from the ResourceManager
  private var maxMemory = None: Option[Long]
  private var maxCores = None: Option[Int]

  import runtime.protobuf.ProtoConversions.ActorRef._
  implicit val sys = context.system

  private val pendingTasks = mutable.Queue.empty[(ArcTask, String)]
  private var launchedTasks = ArrayBuffer.empty[(ArcTask, Container)]

  implicit val timeout = Timeout(2 seconds)

  override def preStart(): Unit = {
    initYarnClients()
    appmaster ! YarnTaskMasterUp(self)
  }

  def receive = {
    case YarnTaskTransferred(binPath, profile, task) =>
      log.info("Bin has been uploaded to: " + binPath)
      allocateContainer(profile, task, binPath)
    case YarnExecutorUp(taskId: Int) =>
      launchedTasks.find(_._1.id.get == taskId) match {
        case Some((_task, container)) =>
          sender() ! YarnExecutorStart(_task)
        case None =>
          sender() ! "fail"
      }
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


  private def allocateContainer(profile: ArcProfile, task: ArcTask, bin: String): Unit = {
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
        pendingTasks.enqueue((task, bin))
      }
    }
  }

  private def shutdown(s: FinalApplicationStatus, message: String): Unit = {
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
      val appMasterAddr = appmaster.path.address
      val appMasterStr = appmaster.path.toStringWithAddress(appMasterAddr)
      val selfAddr = ExternalAddress(context.system).addressForAkka
      val taskMasterStr = self.path.toStringWithAddress(selfAddr)
      val stateMasterAddr = statemaster.path.address
      val stateMasterStr = statemaster.path.toStringWithAddress(stateMasterAddr)
      list.asScala.foreach { container =>
        // For now, just assume it will fit the resources
        if (pendingTasks.nonEmpty) {
          val (task, bin) = pendingTasks.dequeue()
          log.info("TASK: " + task)
          val id = task.id.getOrElse(-1)
          val ctx = YarnTaskExecutor.context(taskMasterStr, appMasterStr, stateMasterStr, jobId, id, bin)
          log.info("Starting Container")
          nmClient.startContainerAsync(container, ctx)
          launchedTasks += ((task, container))
        }
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
