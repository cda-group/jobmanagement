package runtime.appmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.http.scaladsl.Http
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import runtime.appmanager.actors.MetricAccumulator.{StateManagerMetrics, TaskManagerMetrics}
import runtime.appmanager.rest.RestService
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AppManager {
  final case class ArcAppRequest(app: ArcApp)
  final case class ArcDeployRequest(priority: Int, locality: Boolean, tasks: Seq[ArcTask])
  final case class ArcAppStatus(id: String)
  final case class KillArcAppRequest(id: String)
  final case object ResourceManagerUnavailable
  final case object ListApps
  final case object ListAppsWithDetails
  final case object StateMasterError
  type ArcAppId = String
}

/**
  * An Abstract class that can be extended to enable support
  * for multiple cluster managers (Standalone or YARN/Mesos)
  */
abstract class AppManager extends Actor with ActorLogging with AppManagerConfig {
  import AppManager._
  import ClusterListener._
  // For Akka HTTP (REST)
  protected implicit val materializer = ActorMaterializer()
  protected implicit val system = context.system
  protected implicit val ec = context.system.dispatcher

  // Timeout for Futures
  protected implicit val timeout = Timeout(2.seconds)

  // Fields
  protected var appMasters = mutable.IndexedSeq.empty[ActorRef]
  protected var appMasterId: Long = 0 // unique id for each AppMaster that is created
  protected var appMap = mutable.HashMap[ArcAppId, ActorRef]()

  protected var stateManagers = mutable.IndexedSeq.empty[Address]
  protected var stateManagerReqs: Int = 0

  /**
    * In Yarn mode, only metrics from StateManagers are gathered,
    * while in Standalone, TaskManagers and StateManagers.
    */
  protected val metricAccumulator = context.actorOf(MetricAccumulator())

  // Initialize REST service
  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    val rest = new RestService(self)
    Http().bindAndHandle(rest.route, interface, restPort)
  }

  // Common Messages
  def receive = {
    case ArcAppRequest(_) if stateManagers.isEmpty =>
      sender() ! "No StateManagers available"
    case kill@KillArcAppRequest(id) =>
      appMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward kill
        case None =>
          sender() ! s"Could not locate AppMaster connected to $id"
      }
    case s@ArcAppStatus(id) =>
      appMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward s
        case None =>
          sender() ! s"Could not locate AppMaster connected to $id"
      }
    case t@TaskManagerMetrics =>
      metricAccumulator forward t
    case s@StateManagerMetrics =>
      metricAccumulator forward s
    case r@ArcAppMetricRequest(id) =>
      appMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward r
        case None =>
          sender() ! ArcAppMetricFailure("Could not locate the app on this AppManager")
      }
    case ListApps =>
      gatherApps() pipeTo sender()
    case StateManagerRegistration(addr) =>
      stateManagers = stateManagers :+ addr
    case StateManagerRemoved(addr) =>
      stateManagers = stateManagers.filterNot(_ == addr)
    case StateManagerUnreachable(addr) =>
    // Handle
    case Terminated(ref) =>
      // AppMaster was terminated somehow
      appMasters = appMasters.filterNot(_ == ref)
      appMap.find(_._2 == ref) map { m => appMap.remove(m._1) }
  }

  /** Lists all jobs to the user
    *
    * @return Future containing statuses
    */
  protected def gatherApps(): Future[Seq[ArcApp]] = {
    Future.sequence(appMap.map { app =>
      (app._2 ? ArcAppStatus(app._1)).mapTo[ArcApp]
    }.toSeq)
  }

  protected def getStateMaster(amRef: ActorRef, app: ArcApp): Future[StateMasterConn] = {
    val smAddr = stateManagers(stateManagerReqs % stateManagers.size)
    val smSelection = context.actorSelection(ActorPaths.stateManager(smAddr))
    import runtime.protobuf.ProtoConversions.ActorRef._
    smSelection ? StateManagerJob(amRef, app) flatMap {
      case s@StateMasterConn(_,_) => Future.successful(s)
    } recoverWith {
      case t: akka.pattern.AskTimeoutException => Future.failed(t)
    }
  }
}


/**
  * AppManager that uses YARN as its Cluster Manager
  */
class YarnAppManager extends AppManager {
  import AppManager._
  import akka.pattern._

  override def receive = super.receive orElse {
    case ArcAppRequest(arcApp) =>
      val appMaster = context.actorOf(YarnAppMaster(arcApp), Identifiers.APP_MASTER+appMasterId)
      appMasterId +=1
      appMasters = appMasters :+ appMaster
      appMap.put(arcApp.id, appMaster)

      // Enable DeathWatch
      context watch appMaster

      // Create a state master that is linked with the AppMaster and TaskMaster
      getStateMaster(appMaster, arcApp) recover {case _ => StateMasterError} pipeTo appMaster

      val response = "Processing App: " + arcApp.id + "\n"
      sender() ! response
  }
}

object YarnAppManager {
  def apply(): Props = Props(new YarnAppManager())
}


/**
  * AppManager that uses the Standalone Cluster Manager
  */
class StandaloneAppManager extends AppManager {
  import AppManager._
  import StandaloneAppManager._
  import ClusterListener._

  private var resourceManager = None: Option[Address]

  override def receive = super.receive orElse {
    case RmRegistration(rm) if resourceManager.isEmpty =>
      resourceManager = Some(rm)
    case ArcAppRequest(_) if resourceManager.isEmpty =>
      sender() ! ResourceManagerUnavailable
    case ArcAppRequest(arcApp) =>
      // The AppManager has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      val appMaster = context.actorOf(StandaloneAppMaster(arcApp, resourceManager.get)
        , Identifiers.APP_MASTER + appMasterId)
      appMasterId += 1
      appMasters = appMasters :+ appMaster
      appMap.put(arcApp.id, appMaster)

      // Enable DeathWatch
      context watch appMaster

      // Create a state master that is linked with the AppMaster and TaskMaster
      getStateMaster(appMaster, arcApp) recover {case _ => StateMasterError} pipeTo appMaster

      val response = "Processing App: " + arcApp.id + "\n"
      sender() ! response
    case u@UnreachableRm(rm) =>
      // Foreach active AppMaster, notify status
      //resourceManager = None
      appMasters.foreach(_ forward u)
    case RmRemoved(rm) =>
      resourceManager = None
  }
}

object StandaloneAppManager {
  final case class AppMasterInit(app: ArcApp, rmAddr: Address, stateMaster: ActorRef)
  def apply(): Props = Props(new StandaloneAppManager())
}
