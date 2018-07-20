package runtime.appmanager.rest.routes

import java.util.UUID

import akka.actor.ActorRef
import runtime.appmanager.actors.AppManager._
import runtime.common.{Identifiers, Utils}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import runtime.appmanager.rest.JsonConverter
import runtime.protobuf.messages.{ArcJob, ArcJobMetricRequest, ArcJobMetricResponse, ArcProfile}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class JobRoute(appManager: ActorRef)(implicit val ec: ExecutionContext) extends JsonConverter {
  implicit val timeout = Timeout(2.seconds)

  val route: Route =
    pathPrefix("jobs") {
      path("deploy") {
        deploy
      }~
        path("metrics" / Segment) { jobId: String =>
          complete(fetchJobMetrics(jobId))
        }~
        path("kill" / Segment) { jobId: String =>
          complete("killing job...")
        }~
        path("status" / Segment) { jobId: String =>
          complete(jobStatus(jobId))
        }~
        path("list") {
          complete("list all jobs")
        }~
        path("listfull") {
          complete("list all jobs but with details")
        }
      }


  private def fetchJobMetrics(id: String): Future[ArcJobMetricResponse] =
    (appManager ? ArcJobMetricRequest(id)).mapTo[ArcJobMetricResponse]

  private def killJob(id: String): Future[String] =
    (appManager ? KillArcJobRequest(id)).mapTo[String]


  private def jobStatus(id: String): Future[ArcJob] = {
    (appManager ? ArcJobStatus(id)).mapTo[ArcJob]
  }

  private def listJobs(): Future[Any] = {
    (appManager ? ListJobs).mapTo[String]
  }


  private def listJobsWithDetails(): Future[Any] = {
    (appManager ? ListJobsWithDetails).mapTo[String]
  }

  /**
    * Job Deployment Route
    * @return Route to handle deployment
    */
  private def deploy: Route = {
    entity(as[ArcDeployRequest]) { req =>
      val indexedTasks = req.tasks
        .zipWithIndex
        .map(m => m._1.copy(id = Some(m._2+1)))

      val arcJob = ArcJob(UUID.randomUUID().toString, testResourceProfile(),
        indexedTasks, status = Some(Identifiers.ARC_JOB_DEPLOYING))
      val jobRequest = ArcJobRequest(arcJob)
      appManager ! jobRequest
      complete("Processing Job: " + arcJob.id + "\n")
    }
  }

  def testResourceProfile(): ArcProfile =
    ArcProfile(2.0, 2000) // 2.0 cpu core & 2000MB mem

}