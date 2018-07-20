package runtime.taskmaster.yarn

import java.nio.ByteBuffer
import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest
import org.apache.hadoop.yarn.client.api.async.{AMRMClientAsync, NMClientAsync}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import runtime.protobuf.messages.{YarnTaskMasterUp, YarnTaskTransferred}

import scala.concurrent.duration._


object TaskMaster {
  def apply(appmaster: ActorRef, jobId: String): Props =
    Props(new TaskMaster(appmaster, jobId))
}

/**
  * The TaskMaster for Yarn will act as Yarn's ApplicationMaster
  */
class TaskMaster(appmaster: ActorRef, jobId: String) extends Actor with ActorLogging {

  private var rmClient: AMRMClientAsync[ContainerRequest] = _
  private var nmClient: NMClientAsync = _
  private val conf = new YarnConfiguration()

  implicit val sys = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

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
  }


  // AMRM Client Async Callbacks

  private def AMRMHandler: AMRMClientAsync.AbstractCallbackHandler =
    new AMRMClientAsync.AbstractCallbackHandler {
    override def onContainersAllocated(list: util.List[Container]): Unit = ???
    override def onContainersCompleted(list: util.List[ContainerStatus]): Unit = ???
    override def onContainersUpdated(list: util.List[UpdatedContainer]): Unit = ???
    override def onShutdownRequest(): Unit = ???
    override def getProgress: Float = ???
    override def onNodesUpdated(list: util.List[NodeReport]): Unit = ???
    override def onError(throwable: Throwable): Unit = ???
  }

  // NM Async client Callbacks

  private def NMHandler: NMClientAsync.AbstractCallbackHandler = new NMClientAsync.AbstractCallbackHandler {
    override def onGetContainerStatusError(containerId: ContainerId, throwable: Throwable): Unit = ???
    override def onContainerStatusReceived(containerId: ContainerId, containerStatus: ContainerStatus): Unit = ???
    override def onContainerResourceIncreased(containerId: ContainerId, resource: Resource): Unit = ???
    override def onStopContainerError(containerId: ContainerId, throwable: Throwable): Unit = ???
    override def onContainerStopped(containerId: ContainerId): Unit = ???
    override def onIncreaseContainerResourceError(containerId: ContainerId, throwable: Throwable): Unit = ???
    override def onStartContainerError(containerId: ContainerId, throwable: Throwable): Unit = ???
    override def onUpdateContainerResourceError(containerId: ContainerId, throwable: Throwable): Unit = ???
    override def onContainerStarted(containerId: ContainerId, map: util.Map[String, ByteBuffer]): Unit = ???
    override def onContainerResourceUpdated(containerId: ContainerId, resource: Resource): Unit = ???
  }


}
