package runtime.appmanager.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import runtime.common._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import runtime.appmanager.utils.AppManagerConfig
import runtime.common.models.{ArcJob, WeldJob, WeldTask, WeldTaskCompleted}
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable


object AppManager {
  def apply(): Props = Props(new AppManager)
  case class AppMasterInit(job: ArcJob, rmAddr: Address)
  case object ResourceManagerUnavailable
  case class ArcJobRequest(job: ArcJob)
}

class AppManager extends Actor with ActorLogging with AppManagerConfig {
  import ClusterListener._
  import AppManager._

  // For Akka HTTP
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system

  implicit val weldTaskFormat = jsonFormat3(WeldTask.apply)
  implicit val weldJobFormat = jsonFormat1(WeldJob.apply)

  // Job storage
  var weldTasks = IndexedSeq.empty[WeldTask]

  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    Http().bindAndHandle(headRoute, interface, restPort)
  }

  // Just a single resourcemananger for now
  var resourceManager = None: Option[Address]
  var appMasters = mutable.IndexedSeq.empty[ActorRef]
  var appMasterId: Long = 0 // unique id for each AppMaster that is created

  def receive = {
    case RmRegistration(rm) =>
      resourceManager = Some(rm)
    case UnreachableRm(rm) =>
      // Foreach active AppMaster, notify status
      resourceManager = None
    case RmRemoved(rm) =>
      resourceManager = None
    case ArcJobRequest(job) =>
      // The driver has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create a AppMaster to deal with the job
          val appMaster = context.actorOf(AppMaster(), Identifiers.APP_MASTER+appMasterId)
          appMasterId +=1
          appMasters = appMasters :+ appMaster

          // Enable DeathWatch
          context watch appMaster

          // Send the job to the AppMaster actor and be done with it
          appMaster ! AppMasterInit(job, rm)
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
    case Terminated(ref) =>
      // AppMaster was terminated somehow
      appMasters = appMasters.filterNot(_ == ref)
    case _ =>
  }

  val headRoute: Route =
    pathPrefix("api") {
      pathPrefix("v1") {
        jobRoute
      }
    }

  val jobRoute =
    pathPrefix("job") {
      path("submit") {
        entity(as[WeldJob]) { job =>
          job.tasks.foreach { t => weldTasks = weldTasks :+ t}
          val testJob = ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile(), job)
          val jobRequest = ArcJobRequest(testJob)
          self ! jobRequest
          complete("Processing Job: " + job + "\n")
        }
      }~
      path("status") {
        complete(weldTasks + "\n")
      }
    }




}
