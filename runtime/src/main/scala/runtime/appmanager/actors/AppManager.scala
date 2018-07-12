package runtime.appmanager.actors


import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import runtime.appmanager.actors.MetricAccumulator.{StateManagerMetrics, TaskManagerMetrics}
import runtime.common._
import runtime.appmanager.rest.RestService
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.messages._

import scala.collection.mutable


object AppManager {
  def apply(): Props = Props(new AppManager)
  case class AppMasterInit(job: ArcJob, rmAddr: Address)
  case object ResourceManagerUnavailable
  case class ArcJobRequest(job: ArcJob)
  case object WeldTasksStatus
  case class TaskReport(tasks: IndexedSeq[WeldTask])
  type ArcJobID = String
}

/**
  * This implementation is very experimental at this point
  */
class AppManager extends Actor with ActorLogging with AppManagerConfig {
  import ClusterListener._
  import AppManager._

  // For Akka HTTP
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system
  implicit val ec = context.system.dispatcher

  // temp task storage
  var weldTasks = IndexedSeq.empty[WeldTask]

  // MetricAccumulator (Cluster "change name?")
  val metricAccumulator = context.system.actorOf(MetricAccumulator())

  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    val rest = new RestService(self)
    Http().bindAndHandle(rest.route, interface, restPort)
  }


  // Just a single resourcemananger for now
  var resourceManager = None: Option[Address]
  var appMasters = mutable.IndexedSeq.empty[ActorRef]
  var appMasterId: Long = 0 // unique id for each AppMaster that is created
  var appJobMap = mutable.HashMap[ArcJobID, ActorRef]()

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

      arcJob.job.tasks.foreach { t => weldTasks = weldTasks :+ t}

      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create a AppMaster to deal with the job
          val appMaster = context.actorOf(AppMaster(), Identifiers.APP_MASTER+appMasterId)
          appMasterId +=1
          appMasters = appMasters :+ appMaster
          appJobMap.put(arcJob.id, appMaster)

          // Enable DeathWatch
          context watch appMaster

          // Send the job to the AppMaster actor and be done with it
          appMaster ! AppMasterInit(arcJob, rm)
        case None =>
          sender() ! ResourceManagerUnavailable
      }
    case WeldTaskCompleted(task) =>
      weldTasks = weldTasks.map {t =>
        if (task.vec == t.vec && task.expr == t.expr)
          task
        else
          t
      }
    case WeldTasksStatus =>
      sender() ! TaskReport(weldTasks)
    case t@TaskManagerMetrics =>
      metricAccumulator forward t
    case s@StateManagerMetrics =>
      metricAccumulator forward s
    case r@ArcJobMetricRequest(id) =>
      appJobMap.get(id) match {
        case Some(aMaster) =>
          aMaster forward r
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
