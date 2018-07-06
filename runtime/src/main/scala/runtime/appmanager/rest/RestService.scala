package runtime.appmanager.rest

import akka.actor.ActorRef

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import runtime.appmanager.rest.routes.JobRoute
import runtime.appmanager.utils.AppManagerConfig

class RestService(appManager: ActorRef)(implicit val ec: ExecutionContext)
  extends AppManagerConfig {

  private[rest] val jobRoute = new JobRoute(appManager)


  val route =
    pathPrefix("api") {
      pathPrefix(restVersion) {
        jobRoute.route
      }
    }
}
