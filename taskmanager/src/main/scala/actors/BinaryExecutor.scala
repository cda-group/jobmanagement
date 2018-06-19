package actors

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import utils.TaskManagerConfig

import scala.concurrent.duration._

object BinaryExecutor {
  def apply(binPath: String): Props =
    Props(new BinaryExecutor(binPath))
  case object HealthCheck
}

/** Initial PoC for executing binaries and "monitoring" them
  *
  * @param binPath path to the rust binary
  */
class BinaryExecutor(binPath: String)
  extends Actor with ActorLogging with TaskManagerConfig {
  var healthChecker = None: Option[Cancellable]
  val runtime = Runtime.getRuntime
  var process = None: Option[Process]

  import BinaryExecutor._
  import context.dispatcher

  override def preStart(): Unit = {
    process = Some(runtime.exec(binPath))

    healthChecker = Some(context.system.scheduler.schedule(
      binaryExecutorHealthCheck.milliseconds,
      binaryExecutorHealthCheck.milliseconds,
      self,
      HealthCheck
    ))
  }

  def receive = {
    case HealthCheck =>
      process match {
        case Some(p) =>
          if (p.isAlive) {
            log.info("bin: " + binPath + " is alive")
          } else {
            log.info("bin: " + binPath + " has died or stopped executing")
            log.info("Stopping binaryexecutor: " + self)
            healthChecker.map(_.cancel())
            context.stop(self)
          }
        case None =>
          log.error("Something went wrong..")
      }
    case _ =>
  }

}
