package runtime.driver.actors

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Cancellable, Props}
import akka.util.Timeout
import akka.pattern._
import runtime.common._
import runtime.driver.utils.DriverConfig

import scala.concurrent.Future
import scala.concurrent.duration._

object AppMaster {
  def apply(): Props = Props(new AppMaster)
}

/** Actor that handles an ArcJob
  *
  * One AppMaster is created per ArcJob. If a slot allocation is successful,
  * the AppMaster communicates directly with the BinaryManager and can do the following:
  * 1. Instruct BinaryManager to execute binaries
  * 2. Release the slots
  * 3. to be added...
  */
class AppMaster extends Actor with ActorLogging with DriverConfig {
  import AppManager._
  import runtime.common.Types._

  var binaryManager = None: Option[BinaryManagerRef]
  var keepAliveTicker = None: Option[Cancellable]

  // For futures
  implicit val timeout = Timeout(2 seconds)
  import context.dispatcher

  def receive = {
    case AppMasterInit(job, rmAddr) =>
      val resourceManager = context.actorSelection(ActorPaths.resourceManager(rmAddr))
      val req: Future[Either[InetSocketAddress, AllocateResponse]] = allocateRequest(job, resourceManager) flatMap {
        case AllocateSuccess(_, bm) =>
          keepAliveTicker = keepAlive(bm)
          binaryManager = Some(bm)
          requestChannel(bm) flatMap {
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
          binaryTransfer(server, binaryManager.get)
        case Right(e) =>
          log.info("Allocate Request failed: " + e)
      }
    case r@ReleaseSlots =>
      binaryManager.foreach(_ ! r)
    case w@WeldTaskCompleted(t) =>
      log.info("AppMaster received finished WeldTask")
      context.parent ! w
    case BinaryManagerFailure =>
      // Unexpected failure by the BinaryManager
      // Handle it
      keepAliveTicker.map(_.cancel())
    case _ =>
  }

  private def allocateRequest(job: ArcJob, rm: ActorSelection): Future[AllocateResponse] = {
    rm ? job.copy(masterRef = Some(self)) flatMap {
      case r: AllocateResponse => Future.successful(r)
    }
  }

  private def requestChannel(bm: BinaryManagerRef): Future[Option[InetSocketAddress]] = {
    bm ? BinariesCompiled flatMap {
      case BinaryTransferConn(addr) =>
        Future.successful(Some(addr))
      case BinaryTransferError =>
        Future.successful(None)
    }
  }

  // TODO: add actual logic
  private def binaryTransfer(server: InetSocketAddress, bm: BinaryManagerRef): Future[Unit] = {
    Future {
      val binarySender = context.actorOf(BinarySender(server, weldRunnerBin(), bm))
    }
  }

  /**
    * While compilation of binaries is in progress, notify
    * the BinaryManager to keep the slot contract alive.
    * @param binaryManager ActorRef to the BinaryManager
    * @return Cancellable Option
    */
  private def keepAlive(binaryManager: BinaryManagerRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      appMasterKeepAlive.milliseconds) {
      binaryManager ! BMHeartBeat
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
