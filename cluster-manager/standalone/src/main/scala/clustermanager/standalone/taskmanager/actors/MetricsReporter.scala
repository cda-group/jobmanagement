package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, Cancellable, Props}
import clustermanager.standalone.taskmanager.isolation.ContainerMetrics
import runtime.protobuf.messages.Container

import scala.concurrent.duration._

object MetricsReporter {
  case object Tick
  case object StartReporting
  def apply(container: Container, metrics: ContainerMetrics): Props =
    Props(new MetricsReporter(container, metrics))
}

class MetricsReporter(container: Container, metrics: ContainerMetrics) extends Actor with ActorLogging {
  import MetricsReporter._

  private var ticker: Option[Cancellable] = None
  private implicit val ec = context.dispatcher

  override def postStop(): Unit =
    ticker.map(_.cancel())


  def receive = {
    case Tick =>
      log.info(s"Logging metrics for container ${container.jobId}")
      log.info(s"Memory fail count: " + metrics.getContainerMemFailcount)
      log.info("Memory limit for whole container: " + metrics.getMemoryLimit(container.containerId))
      log.info("Memory Usage for whole container: " + metrics.getContainerMemoryUsage)
    case StartReporting =>
      ticker = startTicker()
    case _ =>
  }

  private def startTicker(): Option[Cancellable] = {
    Some(context.system.scheduler.schedule(
      500.milliseconds,
      500.milliseconds,
      self,
      Tick
    ))
  }
}
