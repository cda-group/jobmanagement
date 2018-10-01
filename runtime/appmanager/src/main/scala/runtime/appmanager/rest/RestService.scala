package runtime.appmanager.rest

import akka.actor.ActorRef

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import runtime.appmanager.rest.routes.{AppRoute, ClusterRoute}
import runtime.appmanager.utils.AppManagerConfig

private[appmanager] class RestService(appManager: ActorRef)(implicit val ec: ExecutionContext)
  extends AppManagerConfig {

  private val appRoute = new AppRoute(appManager)
  private val clusterRoute = new ClusterRoute(appManager)


  val route =
    pathPrefix("api") {
      pathPrefix(restVersion) {
        clusterRoute.route~
        appRoute.route
      }
    }

}
