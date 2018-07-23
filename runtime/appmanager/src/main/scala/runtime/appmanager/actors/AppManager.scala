package runtime.appmanager.actors


import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, UnreachableMember}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import runtime.appmanager.actors.AppManager.ArcJobID
import runtime.appmanager.actors.ArcAppManager.AppMasterInit
import runtime.appmanager.actors.MetricAccumulator.{StateManagerMetrics, TaskManagerMetrics}
import runtime.appmanager.rest.RestService
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.{ActorPaths, Identifiers}
import runtime.protobuf.messages._

import scala.collection.mutable
import scala.concurrent.Future

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
  * An Abstract class that can be extended to enable support multiple
  * for multiple cluster managers (Standalone or YARN/Mesos)
  */
abstract class AppManager extends Actor with ActorLogging with AppManagerConfig {
  // For Akka HTTP (REST)
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system
  implicit val ec = context.system.dispatcher

  // Fields
  var appMasters = mutable.IndexedSeq.empty[ActorRef]
  var appMasterId: Long = 0 // unique id for each AppMaster that is created
  var appJobMap = mutable.HashMap[ArcJobID, ActorRef]()

  // Initialize REST service
  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    val rest = new RestService(self)
    Http().bindAndHandle(rest.route, interface, restPort)
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


  def receive = {
    case ArcJobRequest(_) if stateManagers.isEmpty =>
      log.info("NO STATEMANAGER AVAILABLE")
      sender() ! "No stateManagers available"
      // Fix...
    case ArcJobRequest(arcJob) =>
      val appMaster = context.actorOf(YarnAppMaster(arcJob), Identifiers.APP_MASTER+appMasterId)
      appMasterId +=1
      appMasters = appMasters :+ appMaster
      appJobMap.put(arcJob.id, appMaster)

      // Enable DeathWatch
      context watch appMaster

      // Create a state master that is linked with the AppMaster and TaskMaster
      getStateMaster(appMaster, arcJob) recover {case _ => StateMasterError} pipeTo appMaster
    case kill@KillArcJobRequest(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward kill
        case None =>
          sender() ! "Could not locate AppMaster"
      }
    case s@ArcJobStatus(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward s
        case None =>
          sender() ! "Fail"
      }
    case r@ArcJobMetricRequest(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward r
        case None =>
          sender() ! ArcJobMetricFailure("Could not locate the Job on this AppManager")
      }
    case Terminated(ref) =>
      // AppMaster was terminated somehow
      appMasters = appMasters.filterNot(_ == ref)
      appJobMap.find(_._2 == ref) map { m => appJobMap.remove(m._1) }
    case StateManagerRegistration(addr) =>
      stateManagers = stateManagers :+ addr
    case StateManagerRemoved(addr) =>
      stateManagers = stateManagers.filterNot(_ == addr)
    case StateManagerUnreachable(addr) =>
      // Handle
    case _ =>
  }

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
  * AppManager that uses Standalone Cluster Manager
  */
class ArcAppManager extends AppManager{
  import AppManager._
  import ClusterListener._

  var resourceManager = None: Option[Address]

  // MetricAccumulator (Cluster "change name?")
  val metricAccumulator = context.actorOf(MetricAccumulator())

  def receive = {
    case RmRegistration(rm) if resourceManager.isEmpty =>
      resourceManager = Some(rm)
    case u@UnreachableRm(rm) =>
      // Foreach active AppMaster, notify status
      //resourceManager = None
      appMasters.foreach(_ forward u)
    case RmRemoved(rm) =>
      resourceManager = None
    case ArcJobRequest(arcJob) =>
      // The AppManager has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create a AppMaster to deal with the job
          val appMaster = context.actorOf(ArcAppMaster(), Identifiers.APP_MASTER + appMasterId)
          appMasterId += 1
          appMasters = appMasters :+ appMaster
          appJobMap.put(arcJob.id, appMaster)

          // Enable DeathWatch
          context watch appMaster

          // Send the job to the AppMaster actor and be done with it
          appMaster ! AppMasterInit(arcJob, rm)
        case None =>
          sender() ! ResourceManagerUnavailable
      }
    case kill@KillArcJobRequest(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward kill
        case None =>
          sender() ! "Could not locate AppMaster"
      }
    case s@ArcJobStatus(id) =>
      appJobMap.get(id) match {
        case Some(appMaster) =>
          appMaster forward s
        case None =>
          sender() ! "Fail"
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
    case Terminated(ref) =>
      // AppMaster was terminated somehow
      appMasters = appMasters.filterNot(_ == ref)
      appJobMap.find(_._2 == ref) map { m => appJobMap.remove(m._1) }
    case _ =>
  }
}

object ArcAppManager {
  case class AppMasterInit(job: ArcJob, rmAddr: Address)
  def apply(): Props = Props(new ArcAppManager())
}
