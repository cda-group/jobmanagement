package runtime.statemanager.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import runtime.common.Identifiers
import runtime.kompact.{ExecutorTerminated, ExecutorUp, KompactExtension, KompactRef}
import runtime.kompact.messages.{Hello, KompactAkkaMsg}
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
  private var kompactRefs = IndexedSeq.empty[KompactRef]

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._

  private val kompactExtension = KompactExtension(context.system)

  override def preStart(): Unit = {
    context watch appMaster
    kompactExtension.register(self)
  }

  override def postStop(): Unit = {
    kompactExtension.unregister(self)
    kompactRefs.foreach(_.kill())
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
    case KompactAkkaMsg(payload) =>
      log.info(s"Received msg from executor $payload")
    case Terminated(ref) =>
      // AppMaster has been terminated
      // Handle
      // context stop self
    case ExecutorUp(ref) =>
      log.info(s"Kompact Executor up ${ref.srcPath}")
      kompactRefs = kompactRefs :+ ref
      // Enable DeathWatch
      ref kompactWatch self

      val hello = Hello("Akka saying hello from statemaster")
      val welcomeMsg = KompactAkkaMsg().withHello(hello)
      ref ! welcomeMsg
    case ExecutorTerminated(ref) =>
      kompactRefs = kompactRefs.filterNot(_ == ref)
    case _ =>
  }

}
