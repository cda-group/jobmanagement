package runtime.taskmanager.actors

import java.io.{BufferedReader, InputStreamReader}

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import runtime.common.Types.JobManagerRef
import runtime.common.{WeldTask, WeldTaskCompleted}
import runtime.taskmanager.utils.TaskManagerConfig

import scala.concurrent.duration._

object BinaryExecutor {
  def apply(binPath: String, task: WeldTask, jm: JobManagerRef): Props =
    Props(new BinaryExecutor(binPath, task, jm))
  case object HealthCheck
}

/** Initial PoC for executing binaries and "monitoring" them
  *
  * @param binPath path to the rust binary
  */
class BinaryExecutor(binPath: String, task: WeldTask, jm: JobManagerRef)
  extends Actor with ActorLogging with TaskManagerConfig {
  var healthChecker = None: Option[Cancellable]
  val runtime = Runtime.getRuntime
  var process = None: Option[Process]

  import BinaryExecutor._
  import context.dispatcher

  override def preStart(): Unit = {
    val pb = new ProcessBuilder(binPath, task.expr, task.vec)
    process = Some(pb.start())

    healthChecker = Some(context.system.scheduler.schedule(
      binaryExecutorHealthCheck.milliseconds,
      binaryExecutorHealthCheck.milliseconds,
      self,
      HealthCheck
    ))


    val reader = new BufferedReader(new InputStreamReader(process.get.getInputStream))

    var line: String = null
    var res: String = ""
    while ({line = reader.readLine; line != null}) {
      res = line
      println(line)
    }
    process.get.waitFor()
    val updated = task.copy(result = Some(res))
    jm ! WeldTaskCompleted(updated)
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
