package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import runtime.common.messages._

import scala.collection.mutable


object StateMaster {
  def apply(implicit appMaster: ActorRef, job: ArcJob): Props =
    Props(new StateMaster(appMaster, job))
}

/**
  * StateMaster receives metrics from running apps that are
  * connected to a specific AppMaster.
  */
class StateMaster(appMaster: ActorRef, job: ArcJob) extends Actor with ActorLogging {
  import StateMaster._
  var metricMap = mutable.HashMap[WeldTask, ExecutorMetric]()

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.common.messages.ProtoConversions.ActorRef._

  override def preStart(): Unit = {
    // Let appMaster know how to fetch metrics....
    appMaster ! StateMasterConn(self)
    // enable DeathWatch
    context watch appMaster
  }

  def receive = {
    case ArcTaskMetric(task, metric) =>
      log.info("Received task {} with metric {}", task, metric)
      metricMap.put(task, metric)
    case ArcJobMetricRequest(id) if job.id.equals(id) =>
      val report = ArcJobMetricReport(id, metricMap.map(m => ArcTaskMetric(m._1, m._2)).toSeq)
      sender() ! report
    case ArcJobMetricRequest(_) =>
      sender() ! ArcJobMetricFailure("Job ID did not match up")
    case Terminated(ref) =>
      // AppMaster has been terminated
      // Handle
      // context stop self
    case _ =>
  }

}
