package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import _root_.clustermanager.common.executor.ExecutorStats
import akka.cluster.Cluster
import clustermanager.standalone.taskmanager.utils.TaskManagerConfig
import runtime.common.Identifiers
import runtime.protobuf.messages.{ArcTask, ArcTaskMetric, ArcTaskUpdate}

import scala.concurrent.duration._
import scala.util.Try

private[standalone] object TaskExecutor {
  // Refactor
  def apply(binPath: String, task: ArcTask, aMaster: ActorRef, sMaster: ActorRef): Props =
    Props(new TaskExecutor(binPath, task, aMaster, sMaster))
  final case object HealthCheck
  final case class StdOutResult(r: String)
  final case class CreateTaskReader(task: ArcTask)
}

/** Initial PoC for executing binaries and "monitoring" them
  *
  * @param binPath path to the rust binary
  */
private[standalone] class TaskExecutor(binPath: String,
                   task: ArcTask,
                   appMaster: ActorRef,
                   stateMaster: ActorRef
                  )
  extends Actor with ActorLogging with TaskManagerConfig {

  import TaskExecutor._
  import TaskMaster._
  import context.dispatcher

  var healthChecker = None: Option[Cancellable]
  var process = None: Option[Process]
  var monitor = None: Option[ExecutorStats]
  var arcTask = None: Option[ArcTask]

  val selfAddr = Cluster(context.system)
    .selfAddress
    .toString




  def receive = {
    case StartExecution =>
      start()
    case CreateTaskReader(_task) =>
      // Create an actor to read the results from StdOut
      context.actorOf(TaskExecutorReader(process.get, appMaster, _task), "taskreader")
    case HealthCheck =>
      monitor match {
        case Some(stats) =>
          collectMetrics(stats)
        case None =>
          log.info("Could not load monitor")
          shutdown()
      }
    case ArcTaskUpdate(t) =>
      // Gotten the results from the Stdout...
      arcTask = Some(t)
    case Terminated(sMaster) =>
      // StateMaster has been declared as terminated
      // What to do?
    case _ =>
  }

  /** We have been instructed by the TaskMaster to start
    * our binary
    */
  private def start(): Unit = {
    val pb = new ProcessBuilder(binPath, task.expr, task.vec)
    process = Some(pb.start())

    val p = getPid(process.get)
      .toOption

    p match {
      case Some(pid) =>
        ExecutorStats(pid, binPath, selfAddr) match {
          case Some(execStats) =>
            monitor = Some(execStats)
            healthChecker = scheduleCheck()
            // Enable DeathWatch of the StateMaster
            context watch stateMaster
            // Update Status of the Task
            val updatedTask = task.copy(status = Some("running"))
            arcTask = Some(updatedTask)
            appMaster ! ArcTaskUpdate(updatedTask)
            self ! CreateTaskReader(updatedTask)
          case None =>
            log.error("Was not able to create ExecutorStats instance")
            shutdown()
        }
      case None =>
        log.error("TaskExecutor.getPid() requires an UNIX system")
        shutdown()
    }

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
     arcTask match {
       case Some(t) =>
         val updatedTask = t.copy(status = Some(Identifiers.ARC_TASK_KILLED))
         appMaster ! ArcTaskUpdate(updatedTask)
       case None =>
     }
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
