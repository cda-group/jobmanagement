package runtime.appmanager.actors

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Cancellable, Props}
import akka.io.Tcp.{Bind, Bound, CommandFailed}
import akka.util.Timeout
import akka.pattern._
import runtime.appmanager.actors.AppMaster.ArcTask
import runtime.common._
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.messages._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AppMaster {
  def apply(): Props = Props(new AppMaster)
  type ArcTask = Array[Byte]
}

/** Actor that handles an ArcJob
  *
  * One AppMaster is created per ArcJob. If a slot allocation is successful,
  * the AppMaster communicates directly with the TaskMaster and can do the following:
  * 1. Instruct TaskMaster to execute tasks
  * 2. Release the slots
  * 3. to be added...
  */
class AppMaster extends Actor with ActorLogging with AppManagerConfig {
  import AppManager._

  var taskMaster = None: Option[ActorRef]
  var keepAliveTicker = None: Option[Cancellable]

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.common.messages.ProtoConversions.ActorRef._

  def receive = {
    case AppMasterInit(job, rmAddr) =>
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      // Request a TaskSlot for the job
      val req = allocateRequest(job, resourceManager) map {
        case AllocateSuccess(_, tm) =>
          taskMaster = Some(tm)
          // start the heartbeat ticker to notify the TaskMaster that we are alive
          // while we compile
          keepAliveTicker = keepAlive(tm)
          // Compile...
          compileAndTransfer() onComplete {
            case Success(s) => log.info("AppMaster successfully established connection with its TaskMaster")
            case Failure(e) => log.error("Something went wrong during compileAndTransfer: " + e)
          }
        case err =>
          log.error("Allocation for job: " + job.id + " failed with reason: " + err)
      }
    case r@ReleaseSlots =>
      taskMaster.foreach(_ ! r)
    case w@WeldTaskCompleted(t) =>
      log.info("AppMaster received finished WeldTask")
      context.parent ! w
    case TaskMasterFailure() =>
      // Unexpected failure by the TaskMaster
      // Handle it
      keepAliveTicker.map(_.cancel())
    case _ =>
  }

  private def allocateRequest(job: ArcJob, rm: ActorSelection): Future[AllocateResponse] = {
    rm ? job.copy(ref = Some(self)) flatMap {
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
  private def compilation(): Future[Seq[ArcTask]] = Future {
    // compile....
    Seq(weldRunnerBin())
  }

  private def requestChannel(): Future[InetSocketAddress] = {
    import ProtoConversions.InetAddr._
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

  // Just for testing
  private def testBinary(): Seq[Array[Byte]] = {
    Seq(Files.readAllBytes(Paths.get("writetofile")),
      Files.readAllBytes(Paths.get("writetofile2")))
  }

  private def weldRunnerBin(): Array[Byte] =
    Files.readAllBytes(Paths.get("weldrunner"))


}
