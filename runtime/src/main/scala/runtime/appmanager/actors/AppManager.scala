package runtime.appmanager.actors


import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import runtime.appmanager.actors.AppManager.ArcJobID
import runtime.appmanager.actors.ArcAppManager.AppMasterInit
import runtime.appmanager.actors.MetricAccumulator.{StateManagerMetrics, TaskManagerMetrics}
import runtime.common._
import runtime.appmanager.rest.RestService
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.messages._

import scala.collection.mutable

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
  * for multiple cluster managers (Arc-based or YARN/Mesos)
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
  * AppManager that uses YARN as its Resource Manager
  */
class YarnAppManager extends AppManager {
 import AppManager._

  def receive = {
    case ArcJobRequest(arcJob) =>
      // YarnAppMaster
      val appMaster = context.actorOf(YarnAppMaster(arcJob), Identifiers.APP_MASTER+appMasterId)
      appMasterId +=1
      appMasters = appMasters :+ appMaster
      appJobMap.put(arcJob.id, appMaster)

      // Enable DeathWatch
      context watch appMaster

    // Send the job to the AppMaster actor and be done with it
    // appMaster ! AppMasterInit(arcJob, rm)
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
    case _ =>
  }
}

object YarnAppManager {
  case class AppMasterInit(job: ArcJob)
  def apply(): Props = Props(new YarnAppManager())
}


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
