package actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Address, Props, RootActorPath}
import common.{ArcJob, Utils}


object Driver {
  def apply(): Props = Props(new Driver)
}

class Driver extends Actor with ActorLogging {
  import ClusterListener._

  // Just a single resourcemananger for now
  var resourceManager = None: Option[Address]

  def receive = {
    case RmRegistration(rm) =>
      log.info("Rm registration")
      resourceManager = Some(rm)
      // test req
      val target = context.actorSelection(Utils.resourceManagerPath(rm))
      log.info("Sending job")
      target ! ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile())
    case UnreachableRm(rm) =>
      //resourceManager = None
    case RmRemoved(rm) =>
      resourceManager = None
    case _ =>
  }

}
