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


/** Actor that is responsible for allocating containers from the
  * YARN resource manager and then launching them onto NodeManager's.
  * @param appmaster ActorRef of its AppMaster in String format
  * @param statemaster ActorRef of its StateMaster in String format
  * @param jobId ID for the Job
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

  private var pendingTasks = mutable.HashMap[Long, (ArcTask, String)]()
  private var launchedTasks = ArrayBuffer.empty[(ArcTask, Container)]

  // ActorRefs to be passed on
  private val appMasterStr = appmaster.path.
    toStringWithAddress(appmaster.path.address)
  private val stateMasterStr = statemaster.path
    .toStringWithAddress(statemaster.path.address)
  private val selfAddr = ExternalAddress(context.system).addressForAkka
  private val taskMasterStr = self.path.
    toStringWithAddress(selfAddr)

  implicit val timeout = Timeout(2 seconds)

  override def preStart(): Unit = {
    initYarnClients()
    // Once we have started, let the Appmaster know that we are alive.
    appmaster ! YarnTaskMasterUp(self)
  }

  def receive = {
    case YarnTaskTransferred(binPath, task) =>
      log.info("Binary has been uploaded to: " + binPath)
      allocateContainer(task, binPath)
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


  /** Creates a Container request for the task that
    * was just transferred
    * @param task ArcTask
    * @param bin HDFS binary path to the corresponding ArcTask binary
    */
  private def allocateContainer(task: ArcTask, bin: String): Unit = {
    if (maxMemory.isDefined && maxCores.isDefined) {
      if (task.cores <= maxCores.get && task.memory <= maxMemory.get) {
        val resource = Resource.newInstance(task.memory, task.cores)
        val priority = Records.newRecord(classOf[Priority])
        // hardcoded for now
        priority.setPriority(1)

        log.info("Requesting container for task: " + task)
        val allocationId = task.id
          .get
          .toLong

        rmClient.addContainerRequest(new ContainerRequest(resource, null, null, priority, allocationId))
        pendingTasks.put(allocationId, (task, bin))
      } else {
        log.error("Current resources are not enough to request a container for this task")
      }
    } else {
      log.error("Either maxMemory or maxCores has not been set. Cannot allocate container!")
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
    override def onContainersAllocated(containers: util.List[Container]): Unit = {
      log.info("Containers allocated: " + containers.asScala)
      containers.asScala.foreach { container =>
        val allocId = container.getAllocationRequestId
        pendingTasks.get(allocId) match {
          case Some((task: ArcTask, bin:String)) =>
            val ctx = YarnTaskExecutor.context(taskMasterStr, appMasterStr,
              stateMasterStr, jobId, allocId.toInt, bin)
            log.info("Starting Container with task: " + task)
            nmClient.startContainerAsync(container, ctx)
            launchedTasks += ((task, container))
            pendingTasks.remove(allocId)
          case None =>
            log.error("Could not locate task for this allocationRequestId")
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
