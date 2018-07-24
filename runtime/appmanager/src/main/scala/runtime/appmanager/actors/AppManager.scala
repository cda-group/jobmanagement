package runtime.appmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import akka.http.scaladsl.Http
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import runtime.appmanager.actors.AppManager.{ArcJobID, ArcJobStatus}
import runtime.appmanager.actors.MetricAccumulator.{StateManagerMetrics, TaskManagerMetrics}
import runtime.appmanager.rest.RestService
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object AppManager {
  case class ArcJobRequest(job: ArcJob)
  case class ArcDeployRequest(tasks: Seq[ArcTask])
  case class ArcJobStatus(id: String)
  case class KillArcJobRequest(id: String)
  case object ResourceManagerUnavailable
  case object ListJobs
  case object ListJobsWithDetails
  type ArcJobID = String
}

/**
  * An Abstract class that can be extended to enable support
  * for multiple cluster managers (Standalone or YARN/Mesos)
  */
abstract class AppManager extends Actor with ActorLogging with AppManagerConfig {
  import AppManager._
  // For Akka HTTP (REST)
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system
  implicit val ec = context.system.dispatcher

  // Timeout for Futures
  implicit val timeout = Timeout(2.seconds)

  // Fields
  var appMasters = mutable.IndexedSeq.empty[ActorRef]
  var appMasterId: Long = 0 // unique id for each AppMaster that is created
  var appJobMap = mutable.HashMap[ArcJobID, ActorRef]()


  /**
    * In Yarn mode, only metrics from StateManagers are gathered,
    * while in Standalone, TaskManagers and StateManagers.
    */
  val metricAccumulator = context.actorOf(MetricAccumulator())

  // Initialize REST service
  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    val rest = new RestService(self)
    Http().bindAndHandle(rest.route, interface, restPort)
  }

  // Common Messages
  def receive = {
    case kill@KillArcJobRequest(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward kill
        case None =>
          sender() ! s"Could not locate AppMaster connected to $id"
      }
    case s@ArcJobStatus(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward s
        case None =>
          sender() ! s"Could not locate AppMaster connected to $id"
      }
    case t@TaskManagerMetrics =>
      metricAccumulator forward t
    case s@StateManagerMetrics =>
      metricAccumulator forward s
    case r@ArcJobMetricRequest(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward r
        case None =>
          sender() ! ArcJobMetricFailure("Could not locate the Job on this AppManager")
      }
    case ListJobs =>
      gatherJobs() pipeTo sender()
    case Terminated(ref) =>
      // AppMaster was terminated somehow
      appMasters = appMasters.filterNot(_ == ref)
      appJobMap.find(_._2 == ref) map { m => appJobMap.remove(m._1) }
  }

  /** Lists all jobs to the user
    *
    * @return Future containing statuses
    */
  def gatherJobs(): Future[Seq[ArcJobStatus]] = {
    Future.sequence(appJobMap.map { job =>
      (job._2 ? ArcJobStatus(job._1)).mapTo[ArcJobStatus]
    }.toSeq)
  }
}



/**
  * AppManager that uses YARN as its Cluster Manager
  */
class YarnAppManager extends AppManager {
  import ClusterListener._
  import AppManager._
  import YarnAppManager._
  import akka.pattern._
  import scala.concurrent.duration._

  private var stateManagers = mutable.IndexedSeq.empty[Address]
  private var stateManagerReqs: Int = 0

  private implicit val sys = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._
  private val cluster = Cluster(sys)


  override def receive = super.receive orElse {
    case ArcJobRequest(_) if stateManagers.isEmpty =>
      sender() ! "No stateManagers available"
    case ArcJobRequest(arcJob) =>
      val appMaster = context.actorOf(YarnAppMaster(arcJob), Identifiers.APP_MASTER+appMasterId)
      appMasterId +=1
      appMasters = appMasters :+ appMaster
      appJobMap.put(arcJob.id, appMaster)

      // Enable DeathWatch
      context watch appMaster

      // Create a state master that is linked with the AppMaster and TaskMaster
      getStateMaster(appMaster, arcJob) recover {case _ => StateMasterError} pipeTo appMaster

      val response = "Processing job: " + arcJob.id
      sender() ! response
    case StateManagerRegistration(addr) =>
      stateManagers = stateManagers :+ addr
    case StateManagerRemoved(addr) =>
      stateManagers = stateManagers.filterNot(_ == addr)
    case StateManagerUnreachable(addr) =>
      // Handle
    case _ =>
  }

  // Already defined in Standalone
  // TODO: remove duplication
  private def getStateMaster(amRef: ActorRef, job: ArcJob): Future[StateMasterConn] = {
    val smAddr = stateManagers(stateManagerReqs % stateManagers.size)
    val smSelection = context.actorSelection(ActorPaths.stateManager(smAddr))
    implicit val timeout: Timeout = Timeout(2.seconds)
    smSelection ? StateManagerJob(amRef, job) flatMap {
      case s@StateMasterConn(_) => Future.successful(s)
    } recoverWith {
      case t: akka.pattern.AskTimeoutException => Future.failed(t)
    }
  }
}

object YarnAppManager {
  case object StateMasterError
  case class AppMasterInit(job: ArcJob)
  def apply(): Props = Props(new YarnAppManager())
}


/**
  * AppManager that uses the Standalone Cluster Manager
  */
class StandaloneAppManager extends AppManager{
  import AppManager._
  import StandaloneAppManager._
  import ClusterListener._

  var resourceManager = None: Option[Address]

  override def receive = super.receive orElse {
    case RmRegistration(rm) if resourceManager.isEmpty =>
      resourceManager = Some(rm)
    case ArcJobRequest(arcJob) =>
      // The AppManager has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create an AppMaster to deal with the job
          val appMaster = context.actorOf(StandaloneAppMaster(), Identifiers.APP_MASTER + appMasterId)
          appMasterId += 1
          appMasters = appMasters :+ appMaster
          appJobMap.put(arcJob.id, appMaster)

          // Enable DeathWatch
          context watch appMaster

          // Send the job to the AppMaster actor and be done with it
          appMaster ! AppMasterInit(arcJob, rm)

          val response = "Processing job: " + arcJob.id
          sender() ! response
        case None =>
          sender() ! "No ResourceManager available"
      }
    case u@UnreachableRm(rm) =>
      // Foreach active AppMaster, notify status
      //resourceManager = None
      appMasters.foreach(_ forward u)
    case RmRemoved(rm) =>
      resourceManager = None
  }
}

object StandaloneAppManager {
  case class AppMasterInit(job: ArcJob, rmAddr: Address)
  def apply(): Props = Props(new StandaloneAppManager())
}
