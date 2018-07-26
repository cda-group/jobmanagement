package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import runtime.common.Identifiers
import runtime.protobuf.messages._

import scala.collection.mutable


object StateMaster {
  def apply(appMaster: ActorRef, job: ArcJob): Props =
    Props(new StateMaster(appMaster, job))
}

/**
  * StateMaster receives metrics from running apps that are
  * connected to a specific AppMaster.
  */
class StateMaster(appMaster: ActorRef, job: ArcJob) extends Actor with ActorLogging {
  private var metricMap = mutable.HashMap[ArcTask, ExecutorMetric]()

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  override def preStart(): Unit = {
    // enable DeathWatch
    context watch appMaster
  }

  def receive = {
    case ArcTaskMetric(task, metric) =>
      metricMap.put(task, metric)
    case ArcJobMetricRequest(id) if job.id.equals(id) =>
      val report = ArcJobMetricReport(id, metricMap.map(m => ArcTaskMetric(m._1, m._2)).toSeq)
      sender() ! report
    case ArcJobMetricRequest(_) =>
      sender() ! ArcJobMetricFailure("Job ID did not match up")
    case ExecutorTaskExit(task) =>
      // Remove or declare task as "dead"?
    case TaskMasterStatus(Identifiers.ARC_JOB_KILLED) =>
      // react
    case TaskMasterStatus(Identifiers.ARC_JOB_FAILED) =>
    // react
    case TaskMasterStatus(Identifiers.ARC_JOB_SUCCEEDED) =>
    // react
    case Terminated(ref) =>
      // AppMaster has been terminated
      // Handle
      // context stop self
    case _ =>
  }

}
