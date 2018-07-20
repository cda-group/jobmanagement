package runtime.appmanager.actors

import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}
import java.util.jar.{JarEntry, JarOutputStream}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Cancellable, Props}
import akka.cluster.Cluster
import akka.util.Timeout
import akka.pattern._
import _root_.clustermanager.yarn.Client
import clustermanager.yarn.YarnUtils
import org.apache.hadoop.yarn.api.records.ApplicationId
import runtime.appmanager.actors.ArcAppManager.AppMasterInit
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


/** Actor that handles an ArcJob
  */
abstract class AppMaster extends Actor with ActorLogging with AppManagerConfig {
  var stateMaster = None: Option[ActorRef]
  var arcJob = None: Option[ArcJob]


  def updateTask(task: ArcTask): Option[ArcJob] = {
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
  private var yarnTaskMaster = None: Option[ActorRef]
  private val selfAddr = Cluster(context.system).selfAddress

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  override def preStart(): Unit = {
    super.preStart()

    arcJob = Some(job)

    val client = new Client()
    if (client.init()) {
      val me = self.path.toStringWithAddress(selfAddr)
      yarnAppId = client.launchTaskMaster(me, job.id).toOption
    } else {
      log.error("Failed to connect to yarn")
    }
  }

  def receive = {
    case YarnTaskMasterUp(ref) =>
      // Add some timeout, and if we don't receive this msg
      // Fail the job?
      yarnTaskMaster = Some(ref)
      compileAndTransfer() pipeTo ref
    case s: String =>
      println(s)
    case _ =>
  }

  private def compileAndTransfer(): Future[YarnTaskTransferred] = Future {
    YarnUtils.moveToHDFS(job.id, "weldrunner") match {
      case Some(path) => YarnTaskTransferred(path.toString, ArcProfile(1.0, 500))
      case None => YarnTaskTransferred("nej", ArcProfile(1.0, 500))
    }
  }


  // Just testing
  private def createJar(bin: Array[Byte]): Unit = {
    val target = "test.jar"
    val out: JarOutputStream = new JarOutputStream(new FileOutputStream(target))
    Try {
      out.putNextEntry(new JarEntry("weldrunner"))
      out.write(bin)
      out.closeEntry()
    } match {
      case Success(_) =>
        log.info("Created jar")
      case Failure(_) =>
        log.info("Failed creating jar")
    }

    out.close()
  }

  private def weldRunnerBin(): Array[Byte] =
    Files.readAllBytes(Paths.get("weldrunner"))
}

object YarnAppMaster {
  def apply(job: ArcJob): Props =
    Props(new YarnAppMaster(job))
}


/** Actor that handles an ArcJob
  *
  * One AppMaster is created per ArcJob. If a slot allocation is successful,
  * the AppMaster communicates directly with the TaskMaster and can do the following:
  * 1. Instruct TaskMaster to execute tasks
  * 2. Release the slots
  * 3. to be added...
  */
class ArcAppMaster extends AppMaster {
  import AppManager._

  var taskMaster = None: Option[ActorRef]
  //TODO: remove and use DeathWatch instead?
  var keepAliveTicker = None: Option[Cancellable]

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  def receive = {
    case AppMasterInit(job, rmAddr) =>
      arcJob = Some(job)
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      // Request a TaskSlot for the job
      val req = allocateRequest(job, resourceManager) map {
        case AllocateSuccess(_, tm) =>
          taskMaster = Some(tm)
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
    case ArcJobStatus(_) =>
      sender() ! arcJob.get
    case ArcTaskUpdate(task) =>
      arcJob = updateTask(task)
    case TaskMasterFailure() =>
      // Unexpected failure by the TaskMaster
      // Handle it
      keepAliveTicker.map(_.cancel())
    case ArcJobKilled() =>
      keepAliveTicker.map(_.cancel())
      arcJob = arcJob.map(_.copy(status = Some("killed")))
    case KillArcJobRequest(id) =>
      // Shutdown
      // Delete StateMaster
      // Release Slots of TaskManager
    case StateMasterConn(ref) if stateMaster.isEmpty =>
      stateMaster = Some(ref)
    case req@ArcJobMetricRequest(id) if stateMaster.isDefined =>
      (stateMaster.get ? req) pipeTo sender()
    case ArcJobMetricRequest(_) =>
      sender() ! ArcJobMetricFailure("AppMaster has no stateMaster tied to it. Cannot fetch metrics")
    case _ =>
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


object ArcAppMaster {
  def apply(): Props = Props(new ArcAppMaster())
}
