package runtime.appmanager.actors

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props, Terminated}
import akka.cluster.Cluster
import akka.util.Timeout
import akka.pattern._
import clustermanager.yarn.client.Client
import org.apache.hadoop.yarn.api.records.{ApplicationId, YarnApplicationState}
import runtime.appmanager.actors.AppManager.{ArcAppStatus, StateMasterError}
import runtime.appmanager.actors.StandaloneAppMaster.BinaryTask
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
  * An Abstract Actor class that AppMaster's must extend
  */
abstract class AppMaster extends Actor with ActorLogging with AppManagerConfig {
  protected var taskMaster = None: Option[ActorRef]
  protected var stateMasterConn = None: Option[StateMasterConn]
  protected var arcApp = None: Option[ArcApp]

  protected implicit val timeout = Timeout(2 seconds)
  import context.dispatcher
  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  def receive: Receive =  {
    case ArcAppStatus(_) =>
      sender() ! arcApp.get
    case ArcTaskUpdate(task) =>
      arcApp = updateTask(task)
    case TaskMasterStatus(_status) =>
      arcApp = arcApp.map(_.copy(status = Some(_status)))
    case req@ArcAppMetricRequest(id) if stateMasterConn.isDefined =>
      val stateMasterRef: ActorRef = stateMasterConn.get.ref
      (stateMasterRef ? req) pipeTo sender()
    case ArcAppMetricRequest(_) =>
      sender() ! ArcAppMetricFailure("AppMaster has no stateMaster tied to it. Cannot fetch metrics")
  }

  protected def updateTask(task: ArcTask): Option[ArcApp] = {
    arcApp match {
      case Some(app) =>
        val updatedApp = app.copy(tasks = app.tasks.map(s => if (isSameTask(s.id, task.id)) task else s))
        val stillRunning = updatedApp
          .tasks
          .exists(_.result.isEmpty)

        Some(
          if (stillRunning) {
            if (updatedApp.status.get.equals(Identifiers.ARC_APP_DEPLOYING))
              updatedApp.copy(status = Some(Identifiers.ARC_APP_RUNNING))
            else
              updatedApp
          }
          else {
            updatedApp.copy(status = Some(Identifiers.ARC_TASK_KILLED))
          })
      case None =>
        None
    }
  }

  private def isSameTask(a: Option[Int], b: Option[Int]): Boolean = {
    (a,b) match {
      case (Some(x), Some(z)) => x == z
      case _ => false
    }
  }
}

/**
  * Uses YARN to allocate resources and schedule ArcApp's
  */
class YarnAppMaster(app: ArcApp) extends AppMaster {
  private var yarnAppId = None: Option[ApplicationId]
  private val selfAddr = Cluster(context.system).selfAddress
  private val yarnClient = new Client()

  // Handles implicit conversions of ActorRef and ActorRefProto
  import runtime.protobuf.ProtoConversions.ActorRef._

  // Futures
  import context.dispatcher

  import clustermanager.yarn.client._

  override def preStart(): Unit = {
    super.preStart()
    arcApp = Some(app)
  }
  override def receive = super.receive orElse {
    case YarnTaskMasterUp(ref) =>
      // Enable DeathWatch of the TaskMaster
      context watch ref

      taskMaster = Some(ref)

      // Our fake compile method
      arcApp.get.tasks.foreach { t =>
        compileAndTransfer(ref, t)
      }
    case conn@StateMasterConn(ref, _ ) =>
      stateMasterConn = Some(conn)
      val stateMasterRef: ActorRef = conn.ref
      startTaskMaster(stateMasterRef)
    case StateMasterError  =>
      log.error("Could not establish a StateMaster, closing down!")
      context stop self
    case Terminated(ref) =>
      yarnAppId match {
        case Some(id) =>
          handleTaskMasterFailure(id)
        case None =>
          log.error("YARN ApplicationID is not defined, shutting down!")
      }
    case _ =>
  }

  private def startTaskMaster(stateMaster: ActorRef): Unit = {
    log.info("Launching TaskMaster onto the YARN cluster")
    if (yarnClient.init()) {
      // Format ActorRefs into Strings
      val meStr = self.path.
        toStringWithAddress(selfAddr)
      val stateMasterStr = stateMaster.path.
        toStringWithAddress(stateMaster.path.address)
      yarnAppId = yarnClient.launchTaskMaster(meStr, stateMasterStr, app.id).toOption
    } else {
      log.error("Could not establish connection with YARN")
    }
  }

  //TODO: Implement real logic
  // Simulation for now
  private def compileAndTransfer(tMaster: ActorRef, task: ArcTask): Future[Unit] = Future {
    YarnUtils.moveToHDFS(app.id, task.name, "weldrunner") match {
      case Some(path) =>
        tMaster ! YarnTaskTransferred(path.toString, task)
      case None =>
        self ! ArcTaskUpdate(task.copy(status = Some(Identifiers.ARC_TASK_TRANSFER_ERROR)))
    }
  }

  /** TaskMaster has been terminated. Fetch Application Status
    * from YARN and react accordingly.
    * @param id ApplicationId
    */
  private def handleTaskMasterFailure(id: ApplicationId): Unit = yarnClient.getAppStatus(id) match {
    case Some(YarnApplicationState.FAILED) =>
      // It is in failed state, clean and launch a new taskMaster?
    case Some(YarnApplicationState.KILLED) =>
      // Killed, then clean here
    case Some(YarnApplicationState.RUNNING) =>
      // It is still running, perhaps a network split between AppMaster and TaskMaster
    case Some(YarnApplicationState.SUBMITTED) =>
      // It is submitted but we don't have contact with the TaskMaster
      // Clean and submit again?
    case Some(YarnApplicationState.FINISHED) =>
      // Finished, clean up here
    case Some(YarnApplicationState.NEW) =>
    case Some(YarnApplicationState.NEW_SAVING) =>
    case Some(YarnApplicationState.ACCEPTED) =>
    case None =>
      log.error("Could not fetch YarnApplicationState from YARN, shutting down!")
  }


}

object YarnAppMaster {
  def apply(app: ArcApp): Props =
    Props(new YarnAppMaster(app))
}


/**
  * Uses the Standalone Cluster Manager in order to allocate resources
  * and schedule ArcApp's
  */
private[runtime] class StandaloneAppMaster(app: ArcApp, rmAddr: Address) extends AppMaster {
  import AppManager._

  // Handles implicit conversions of ActorRef and ActorRefProto
  import runtime.protobuf.ProtoConversions.ActorRef._

  private val containers = mutable.HashMap.empty[ActorRef, Container]

  // Futures
  import context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    arcApp = Some(app)
  }

  override def receive = super.receive orElse {
    case conn@StateMasterConn(ref, _) =>
      stateMasterConn = Some(conn)
      // Start Request...
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      // Add ActorRef of ourselves onto the Job
      resourceManager ! app.copy(appMasterRef = Some(self))
    case StateMasterError =>
      log.error("Something went wrong while fetching StateMaster, shutting down!")
      context stop self
    case TaskMasterUp(container, ref) if stateMasterConn.nonEmpty =>
      // Enable DeathWatch of the TaskMaster
      context watch ref
      // Store Reference and to which container it belongs to
      containers.put(ref, container)
      // Notify TaskMaster about our StateMaster
      sender() ! stateMasterConn.get
      taskMaster = Some(ref)

      // If the tasks belonging to the container's tasks have not been compiled,
      // then make sure they are, then transfer them to the TaskMaster
      compileAndTransfer(container, ref) onComplete {
        case Success(v) =>
          log.info("Successfully compiledAndTransferred")
        case Failure(e) =>
          log.error(e.toString)
      }
    case Terminated(ref) =>
      // TaskMaster has been terminated, act accordingly
      containers.get(ref) match {
        case Some(container) =>
        case None =>
      }
  }

  private def compileAndTransfer(c: Container, tMaster: ActorRef): Future[Unit] = Future {
   for {
      tasks <- compilation(c)
      inet <- requestChannel(tMaster)
      transfer <- taskTransfer(inet, tasks, tMaster)
    } yield  transfer
  }

  // temp method
  private def compilation(c: Container): Future[Seq[BinaryTask]] = Future {
    c.tasks.map(task => BinaryTask(weldRunnerBin(), task.name))
  }

  private def requestChannel(tMaster: ActorRef): Future[InetSocketAddress] = {
    import runtime.protobuf.ProtoConversions.InetAddr._
    tMaster ? TasksCompiled() flatMap {
      case TaskTransferConn(inet) => Future.successful(inet)
      case TaskTransferError() => Future.failed(new Exception("TaskMaster failed to Bind Socket"))
    }
  }

  // TODO: add actual logic
  private def taskTransfer(server: InetSocketAddress, tasks: Seq[BinaryTask], tMaster: ActorRef): Future[Unit] = {
    Future {
      tasks.foreach { task =>
        val taskSender = context.actorOf(TaskSender(server, task.bin, tMaster, task.name))
      }
    }
  }

  private def weldRunnerBin(): Array[Byte] =
    Files.readAllBytes(Paths.get("executor"))
}


private[runtime] object StandaloneAppMaster {
  def apply(app: ArcApp, resourceManagerAddr: Address): Props =
    Props(new StandaloneAppMaster(app, resourceManagerAddr))

  final case class BinaryTask(bin: Array[Byte], name: String)
}
