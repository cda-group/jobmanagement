package runtime.appmanager.actors

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Cancellable, Props}
import akka.util.Timeout
import akka.pattern._
import runtime.common._
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.models._

import scala.concurrent.Future
import scala.concurrent.duration._

object AppMaster {
  def apply(): Props = Props(new AppMaster)
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
  import ProtoConversions.ActorRef._

  def receive = {
    case AppMasterInit(job, rmAddr) =>
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      val req: Future[Either[InetSocketAddress, AllocateResponse]] = allocateRequest(job, resourceManager) flatMap {
        case AllocateSuccess(_, tm) =>
          keepAliveTicker = keepAlive(tm)
          taskMaster = Some(tm)
          requestChannel(tm) flatMap {
            case Some(server) =>
              Future.successful(Left(server))
            case None =>
              Future.successful(Right(AllocateError("Could not fetch InetSocketAddress")))
          }
        case e@AllocateFailure(_) =>
          Future.successful(Right(e))
        case e@AllocateError(_) =>
          Future.successful(Right(e))
      }

      req.map {
        case Left(server) =>
          taskTransfer(server, taskMaster.get)
        case Right(e) =>
          log.info("Allocate Request failed: " + e)
      }
    case r@ReleaseSlots =>
      taskMaster.foreach(_ ! r)
    case w@WeldTaskCompleted(t) =>
      log.info("AppMaster received finished WeldTask")
      context.parent ! w
    case TaskMasterFailure =>
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

  private def requestChannel(tm: ActorRef): Future[Option[InetSocketAddress]] = {
    import ProtoConversions.InetAddr._
    tm ? TasksCompiled flatMap {
      case TaskTransferConn(addr) =>
        Future.successful(Some(addr))
      case TaskTransferError =>
        Future.successful(None)
    }
  }

  // TODO: add actual logic
  private def taskTransfer(server: InetSocketAddress, tm: ActorRef): Future[Unit] = {
    Future {
      val taskSender = context.actorOf(TaskSender(server, weldRunnerBin(), tm))
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
      taskMaster ! TaskMasterHeartBeat
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
