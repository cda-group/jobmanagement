package actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Address, Props, RootActorPath}
import common.{JobRequest, Utils}


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
      val target = context.actorSelection(Utils.jobmasterPath(rm))
      target ! JobRequest(UUID.randomUUID().toString)
    case UnreachableRm(rm) =>
      //resourceManager = None
    case RmRemoved(rm) =>
      resourceManager = None
    case _ =>
  }

}
