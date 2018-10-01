package runtime.appmanager.rest.routes


import akka.actor.ActorRef
import runtime.appmanager.actors.AppManager._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import runtime.appmanager.rest.JsonConverter
import runtime.protobuf.messages.{ArcApp, ArcAppMetricRequest, ArcAppMetricResponse}
import runtime.common._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

private[appmanager] class AppRoute(appManager: ActorRef)(implicit val ec: ExecutionContext) extends JsonConverter {
  implicit val timeout = Timeout(2.seconds)

  val route: Route =
    pathPrefix("apps") {
      path("deploy") {
        deploy
      }~
        path("metrics" / Segment) { appId: String =>
          complete(fetchAppMetrics(appId))
        }~
        path("kill" / Segment) { appId: String =>
          complete("killing app...")
        }~
        path("status" / Segment) { appId: String =>
          complete(appStatus(appId))
        }~
        path("list") {
          complete(listApps())
        }~
        path("listfull") {
          complete("list all apps but with details")
        }
      }


  private def fetchAppMetrics(id: String): Future[ArcAppMetricResponse] =
    (appManager ? ArcAppMetricRequest(id)).mapTo[ArcAppMetricResponse]

  private def killApp(id: String): Future[String] =
    (appManager ? KillArcAppRequest(id)).mapTo[String]

  private def appStatus(id: String): Future[ArcApp] =
    (appManager ? ArcAppStatus(id)).mapTo[ArcApp]

  private def listApps(): Future[Seq[ArcApp]] =
    (appManager ? ListApps).mapTo[Seq[ArcApp]]

  private def listAppsWithDetails(): Future[Any] =
    (appManager ? ListAppsWithDetails).mapTo[String]

  private def appRequest(app: ArcApp): Future[String] = {
    val appRequest = ArcAppRequest(app)
    (appManager ? appRequest).mapTo[String]
  }

  /**
    * App Deployment Route
    * @return Route to handle deployment
    */
  private def deploy: Route = {
    entity(as[ArcDeployRequest]) { req =>
      val indexedTasks = req.tasks
        .zipWithIndex
        .map(m => m._1.copy(id = Some(m._2+1)))

      val arcApp = ArcApp(IdGenerator.app(), indexedTasks, req.priority, true,
        status = Some(Identifiers.ARC_APP_DEPLOYING))
      complete(appRequest(arcApp))
    }
  }
}
