package runtime.taskmaster.yarn

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props}

import scala.collection.mutable


object TaskMaster {
  def apply(appmaster: ActorRef): Props = Props(new TaskMaster(appmaster))
}

/**
  * The TaskMaster for Yarn will act as Yarn's ApplicationMaster
  */
class TaskMaster(appmaster: ActorRef) extends Actor with ActorLogging {

  var containers = mutable.HashMap[String, String]()

  override def preStart(): Unit = {
    appmaster ! "Hello"
  }

  def receive = {
    case _ =>
  }

}
