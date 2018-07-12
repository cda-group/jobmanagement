package runtime.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import runtime.common.messages.{ArcTaskMetric, ExecutorTaskExit, WeldTask}
import runtime.taskmanager.utils._

import scala.concurrent.duration._
import scala.util.Try

object TaskExecutor {
  def apply(binPath: String, task: WeldTask, aMaster: ActorRef, sMaster: ActorRef): Props =
    Props(new TaskExecutor(binPath, task, aMaster, sMaster))
  case object HealthCheck
  case class StdOutResult(r: String)
}

/** Initial PoC for executing binaries and "monitoring" them
  *
  * @param binPath path to the rust binary
  */
class TaskExecutor(binPath: String, task: WeldTask, appMaster: ActorRef, stateMaster: ActorRef)
  extends Actor with ActorLogging with TaskManagerConfig {

  var healthChecker = None: Option[Cancellable]
  var process = None: Option[Process]
  var monitor = None: Option[ExecutorStats]

  import TaskExecutor._
  import context.dispatcher

  override def preStart(): Unit = {
    val pb = new ProcessBuilder(binPath, task.expr, task.vec)
    process = Some(pb.start())

    val p = getPid(process.get)
      .toOption

    p match {
      case Some(pid) =>
        ExecutorStats(pid) match {
          case Some(execStats) =>
            monitor = Some(execStats)
            healthChecker = scheduleCheck()
            // Enable DeathWatch of the StateMaster
            context watch stateMaster
            // Create an actor to read the results from StdOut
            context.system.actorOf(TaskExecutorReader(process.get, appMaster, task))
          case None =>
            log.error("Was not able to create ExecutorStats instance")
            shutdown()
        }
      case None =>
        log.error("TaskExecutor.getPid() requires an UNIX system")
        shutdown()
    }
  }

  def receive = {
    case HealthCheck =>
      monitor match {
        case Some(stats) =>
          collectMetrics(stats)
        case None =>
          log.info("Could not load monitor")
          shutdown()
      }
    case Terminated(sMaster) =>
      // StateMaster has been declared as terminated
      // What to do?
    case _ =>
  }

  private def collectMetrics(stats: ExecutorStats): Unit = {
   if (process.isDefined && process.get.isAlive) {
     stats.complete() match {
       case Left(metric) =>
         stateMaster ! ArcTaskMetric(task, metric)
       case Right(err) =>
         log.error(err.toString)
     }
   } else {
     log.info("Process is no longer alive, shutting down!")
     stateMaster ! ExecutorTaskExit(task)
     shutdown()
   }
  }


  /** https://stackoverflow.com/questions/1897655/get-subprocess-id-in-java
    * Only works on Unix based systems. Java 9 Process API has a
    * getPid() Method but we are limited to Java 8.
    */
  private def getPid(p: Process): Try[Long] = Try {
    val field = p.getClass.getDeclaredField("pid")
    field.setAccessible(true)
    field.get(p)
      .toString
      .toLong
  }

  private def scheduleCheck(): Option[Cancellable] = {
    Some(context.system.scheduler.schedule(
      taskExecutorHealthCheck.milliseconds,
      taskExecutorHealthCheck.milliseconds,
      self,
      HealthCheck
    ))
  }

  /**
    * Stop the health ticker and instruct the actor to close down.
    */
  private def shutdown(): Unit = {
    healthChecker.map(_.cancel())
    context.stop(self)
  }

}
