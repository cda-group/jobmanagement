package runtime.appmanager.actors

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Cancellable, Props, Terminated}
import akka.cluster.Cluster
import akka.util.Timeout
import akka.pattern._
import clustermanager.yarn.client.Client
import org.apache.hadoop.yarn.api.records.{ApplicationId, YarnApplicationState}
import runtime.appmanager.actors.AppManager.{ArcJobStatus, StateMasterError}
import runtime.appmanager.actors.StandaloneAppManager.AppMasterInit
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
  * An Abstract Actor class that AppMaster's must extend
  */
abstract class AppMaster extends Actor with ActorLogging with AppManagerConfig {
  protected var taskMaster = None: Option[ActorRef]
  protected var stateMaster = None: Option[ActorRef]
  protected var arcJob = None: Option[ArcJob]

  protected implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive: Receive =  {
    case ArcJobStatus(_) =>
      sender() ! arcJob.get
    case ArcTaskUpdate(task) =>
      arcJob = updateTask(task)
    case TaskMasterStatus(_status) =>
      arcJob = arcJob.map(_.copy(status = Some(_status)))
    case req@ArcJobMetricRequest(id) if stateMaster.isDefined =>
      (stateMaster.get ? req) pipeTo sender()
    case ArcJobMetricRequest(_) =>
      sender() ! ArcJobMetricFailure("AppMaster has no stateMaster tied to it. Cannot fetch metrics")
  }

  protected def updateTask(task: ArcTask): Option[ArcJob] = {
    arcJob match {
      case Some(job) =>
        val updatedJob = job.copy(tasks = job.tasks.map(s => if (isSameTask(s.id, task.id)) task else s))
        val stillRunning = updatedJob
          .tasks
          .exists(_.result.isEmpty)

        Some(
          if (stillRunning) {
            if (updatedJob.status.get.equals(Identifiers.ARC_JOB_DEPLOYING))
              updatedJob.copy(status = Some(Identifiers.ARC_JOB_RUNNING))
            else
              updatedJob
          }
          else {
            updatedJob.copy(status = Some(Identifiers.ARC_TASK_KILLED))
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
  * Uses YARN to allocate resources and schedule ArcJob's.
  */
class YarnAppMaster(job: ArcJob) extends AppMaster {
  private var yarnAppId = None: Option[ApplicationId]
  private val selfAddr = Cluster(context.system).selfAddress
  private val yarnClient = new Client()

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  // Futures
  import context.dispatcher

  import clustermanager.yarn.client._

  override def preStart(): Unit = {
    super.preStart()
    arcJob = Some(job)
  }

  private def startTaskMaster(stateMaster: ActorRef): Unit = {
    log.info("Launching TaskMaster onto the YARN cluster")
    if (yarnClient.init()) {
      // Format ActorRefs into Strings
      val meStr = self.path.
        toStringWithAddress(selfAddr)
      val stateMasterStr = stateMaster.path.
        toStringWithAddress(stateMaster.path.address)
      yarnAppId = yarnClient.launchTaskMaster(meStr, stateMasterStr, job.id).toOption
    } else {
      log.error("Could not establish connection with YARN")
    }
  }

  override def receive = super.receive orElse {
    case YarnTaskMasterUp(ref) =>
      // Enable DeathWatch of the TaskMaster
      context watch ref

      taskMaster = Some(ref)

      // Our fake compile method
      arcJob.get.tasks.foreach { t =>
        compileAndTransfer(ref, t)
      }
    case StateMasterConn(ref) =>
      stateMaster = Some(ref)
      startTaskMaster(ref)
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

  //TODO: Implement real logic
  // Simulation for now
  private def compileAndTransfer(tMaster: ActorRef, task: ArcTask): Future[Unit] = Future {
    YarnUtils.moveToHDFS(job.id, task.name, "weldrunner") match {
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
    case None =>
      log.error("Could not fetch YarnApplicationState from YARN, shutting down!")
  }


}

object YarnAppMaster {
  def apply(job: ArcJob): Props =
    Props(new YarnAppMaster(job))
}


/**
  * Uses the Standalone Cluster Manager in order to allocate resources
  * and schedule ArcJob's
  */
class StandaloneAppMaster extends AppMaster {
  import AppManager._

  //TODO: remove and use DeathWatch instead?
  private var keepAliveTicker = None: Option[Cancellable]

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  // Futures
  import context.dispatcher

  override def receive = super.receive orElse {
    case AppMasterInit(job, rmAddr, sMaster) =>
      stateMaster = Some(sMaster)
      arcJob = Some(job)
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      // Request a TaskSlot for the job
      val req = allocateRequest(job, resourceManager) map {
        case AllocateSuccess(_, tm) =>
          taskMaster = Some(tm)
          // Send responsible StateMaster to the TaskMaster
          taskMaster.get ! StateMasterConn(sMaster)
          // start the heartbeat ticker to notify the TaskMaster that we are alive
          // while we compile
          keepAliveTicker = keepAlive(tm)
          arcJob = arcJob.map(_.copy(status = Some("deploying")))
          // Compile...
          compileAndTransfer() onComplete {
            case Success(s) => log.info("AppMaster successfully established connection with its TaskMaster")
            case Failure(e) =>
              arcJob = arcJob.map(_.copy(status = Some("deploying")))
              log.error("Something went wrong during compileAndTransfer: " + e)
              keepAliveTicker.map(_.cancel())
              // Shutdown or let it be alive until it is killed by "someone"?
              // context stop self
          }
        case err =>
          log.error("Allocation for job: " + job.id + " failed with reason: " + err)
          context stop self
      }
    case r@ReleaseSlots =>
      taskMaster.foreach(_ ! r)
    case KillArcJobRequest(id) =>
      // Shutdown
      // Delete StateMaster
      // Release Slots of TaskManager
  }

  private def allocateRequest(job: ArcJob, rm: ActorSelection): Future[AllocateResponse] = {
    rm ? job.copy(appMasterRef = Some(self)) flatMap {
      case r: AllocateResponse => Future.successful(r)
    }
  }

  private def compileAndTransfer(): Future[Unit] = Future {
   for {
      tasks <- compilation()
      inet <- requestChannel()
      transfer <- taskTransfer(inet, tasks)
    } yield  transfer
  }

  // temp method
  private def compilation(): Future[Seq[Array[Byte]]] = Future {
    // compile....
    Seq(weldRunnerBin())
  }

  private def requestChannel(): Future[InetSocketAddress] = {
    import runtime.protobuf.ProtoConversions.InetAddr._
    taskMaster.get ? TasksCompiled() flatMap {
      case TaskTransferConn(inet) => Future.successful(inet)
      case TaskTransferError() => Future.failed(new Exception("TaskMaster failed to Bind Socket"))
    }
  }

  // TODO: add actual logic
  private def taskTransfer(server: InetSocketAddress, tasks: Seq[Array[Byte]]): Future[Unit] = {
    Future {
      tasks.foreach { task =>
        val taskSender = context.actorOf(TaskSender(server, task, taskMaster.get))
      }
    }
  }

  /**
    * While compilation of binaries is in progress, notify
    * the TaskMaster to keep the slot contract alive.
    * @param taskMaster ActorRef to the TaskMaster
    * @return Cancellable Option
    */
  private def keepAlive(taskMaster: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      appMasterKeepAlive.milliseconds) {
      taskMaster ! TaskMasterHeartBeat()
    })
  }


  private def weldRunnerBin(): Array[Byte] =
    Files.readAllBytes(Paths.get("weldrunner"))
}


object StandaloneAppMaster {
  def apply(): Props = Props(new StandaloneAppMaster())
}
