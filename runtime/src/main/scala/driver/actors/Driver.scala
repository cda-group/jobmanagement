package driver.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import driver.utils.DriverConfig
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable


object Driver {
  def apply(): Props = Props(new Driver)
  case class JobManagerInit(job: ArcJob, rmAddr: Address)

}

class Driver extends Actor with ActorLogging with DriverConfig {
  import ClusterListener._
  import Driver._

  implicit val materializer = ActorMaterializer()
  implicit val system = context.system

  implicit val weldTaskFormat = jsonFormat3(WeldTask)
  implicit val weldJobFormat = jsonFormat1(WeldJob)

  // Job storage
  var weldTasks = IndexedSeq.empty[WeldTask]

  override def preStart(): Unit = {
    log.info("Starting up REST server at " + interface + ":" + restPort)
    Http().bindAndHandle(headRoute, interface, restPort)
  }

  // Just a single resourcemananger for now
  var resourceManager = None: Option[Address]
  var jobManagers = mutable.IndexedSeq.empty[ActorRef]
  var jobManagerId: Long = 0 // unique id for each jobmanager that is created

  def receive = {
    case RmRegistration(rm) =>
      resourceManager = Some(rm)
      // test req
      //val testJob = ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile())
      //val jobRequest = ArcJobRequest(testJob)
      //self ! jobRequest
    case UnreachableRm(rm) =>
      // Foreach active JobManager, notify status
      resourceManager = None
    case RmRemoved(rm) =>
      resourceManager = None
    case ArcJobRequest(job) =>
      // The driver has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create a jobmanager to deal with the job
          // Create and add jobManager to jobManagers
          val jobManager = context.actorOf(JobManager(), Identifiers.JOB_MANAGER+jobManagerId)
          jobManagerId +=1
          jobManagers = jobManagers :+ jobManager

          // Enable DeathWatch
          context watch jobManager

          // Send the job to the JobManager actor and be done with it
          jobManager ! JobManagerInit(job, rm)
        case None =>
          sender() ! "noresourcemanager" // change this..
      }
    case WeldTaskCompleted(task) =>
      weldTasks = weldTasks.map {t =>
        if (task.vec == t.vec && task.expr == t.expr)
          task
        else
          t
      }
    case Terminated(ref) =>
      // JobManager was terminated somehow
      jobManagers = jobManagers.filterNot(_ == ref)
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
