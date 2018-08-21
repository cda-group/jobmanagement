package clustermanager.yarn.taskexecutor

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}
import clustermanager.common.executor.ExecutorStats
import runtime.common.Identifiers
import runtime.protobuf.ExternalAddress
import runtime.protobuf.messages._

import scala.concurrent.duration._
import scala.util.Try

private[yarn] object TaskExecutor {
  def apply(binPath: String,
            taskId: Int,
            appMaster: ActorRef,
            taskMaster: ActorRef,
            stateMaster: ActorRef): Props =
    Props(new TaskExecutor(binPath, taskId, appMaster, taskMaster, stateMaster))
  case object HealthCheck
  case class StdOutResult(r: String)
  case class CreateTaskReader(task: ArcTask)
}

/** Actor that is responsible for executing
  * and monitoring a binary
  * @param binPath path to the binary on the local filesystem
  * @param taskId ID of the task
  * @param appMaster ActorRef of AppMaster
  * @param taskMaster ActorRef of TaskMaster
  * @param stateMaster ActorRef of StateMaster
  */
private[yarn] class TaskExecutor(binPath: String,
                                 taskId: Int,
                                 appMaster: ActorRef,
                                 taskMaster: ActorRef,
                                 stateMaster: ActorRef,
                                ) extends Actor with ActorLogging with TaskExecutorConfig {

  private var healthChecker = None: Option[Cancellable]
  private var process = None: Option[Process]
  private var monitor = None: Option[ExecutorStats]
  private var arcTask = None: Option[ArcTask]
  private val selfAddr = self.path.
    toStringWithAddress(ExternalAddress(context.system).addressForAkka)

  import TaskExecutor._
  import context.dispatcher

  override def preStart(): Unit = {
    // Let our TaskMaster know that we are ready to receive our Task
    taskMaster ! YarnExecutorUp(taskId)
  }

  def receive = {
    case YarnExecutorStart(task) =>
      execute(task)
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
      log.info(s"Lost Contact with our StateMaster $stateMaster")
      // Handle?
    case _ =>
  }

  /** Executes @binPath and initializes the monitoring service
    * @param task ArcTask
    */
  private def execute(task: ArcTask): Unit = {
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

  /** Uses ExecutorStats to gather process metrics
    * to then send to a StateMaster
    * @param stats ExecutorStats instance
    */
  private def collectMetrics(stats: ExecutorStats): Unit = {
   if (process.isDefined && process.get.isAlive) {
     stats.complete() match {
       case Left(metric) =>
         stateMaster ! ArcTaskMetric(arcTask.get, metric)
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
      monitorInterval.milliseconds,
      monitorInterval.milliseconds,
      self,
      HealthCheck
    ))
  }

  /**
    * Stop the health ticker and instruct the actor to close down.
    */
  private def shutdown(): Unit = {
    log.info("Shutting down TaskExecutor")
    healthChecker.map(_.cancel())
    context.system.terminate()
  }

}
