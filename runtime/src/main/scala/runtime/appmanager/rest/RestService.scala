package runtime.appmanager.rest

import akka.actor.ActorRef

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import runtime.appmanager.rest.routes.{ClusterRoute, JobRoute}
import runtime.appmanager.utils.AppManagerConfig

class RestService(appManager: ActorRef)(implicit val ec: ExecutionContext)
  extends AppManagerConfig {

  private[rest] val jobRoute = new JobRoute(appManager)
  private[rest] val clusterRoute = new ClusterRoute(appManager)


  val route =
    pathPrefix("api") {
      pathPrefix(restVersion) {
        clusterRoute.route~
        jobRoute.route
      }
    }

}
