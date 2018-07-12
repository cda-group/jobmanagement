package runtime.appmanager.rest.routes

import java.util.UUID

import akka.actor.ActorRef
import runtime.appmanager.actors.AppManager.{ArcJobRequest, TaskReport, WeldTasksStatus}
import runtime.common.Utils
import runtime.common.messages._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import runtime.appmanager.rest.JsonConverter

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class JobRoute(appManager: ActorRef)(implicit val ec: ExecutionContext) extends JsonConverter {
  implicit val timeout = Timeout(2.seconds)

  val route: Route =
    pathPrefix("jobs") {
      path("submit") {
        entity(as[WeldJob]) { job =>
          val testJob = ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile(), job)
          val jobRequest = ArcJobRequest(testJob)
          appManager ! jobRequest
          complete("Processing Job: " + testJob.id + "\n")
        }
      }~
        path("status") {
          get {
            onSuccess((appManager ? WeldTasksStatus).mapTo[TaskReport]) { res =>
              complete(res)
            }
          }
        }~
        path("metrics" / Segment) { jobId: String =>
          complete(fetchJobMetrics(jobId))
        }
    }


  private def fetchJobMetrics(id: String): Future[ArcJobMetricResponse] = {
    (appManager ? ArcJobMetricRequest(id)).mapTo[ArcJobMetricResponse]
  }


}
