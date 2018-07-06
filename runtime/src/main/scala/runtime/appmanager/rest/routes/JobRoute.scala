package runtime.appmanager.rest.routes

import java.util.UUID

import akka.actor.ActorRef
import runtime.appmanager.actors.AppManager.{ArcJobRequest, TaskReport, WeldTasksStatus}
import runtime.common.Utils
import runtime.common.messages.{ArcJob, WeldJob}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import runtime.appmanager.rest.JsonConverter

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class JobRoute(appManager: ActorRef)(implicit val ec: ExecutionContext) extends JsonConverter {

  val route: Route =
    pathPrefix("job") {
      path("submit") {
        entity(as[WeldJob]) { job =>
          val testJob = ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile(), job)
          val jobRequest = ArcJobRequest(testJob)
          appManager ! jobRequest
          complete("Processing Job: " + job + "\n")
        }
      }~
        path("status") {
          get {
            implicit val timeout = Timeout(2.seconds)
            onSuccess((appManager ? WeldTasksStatus).mapTo[TaskReport]) { res =>
              complete(res)
            }
          }
        }
    }


}
