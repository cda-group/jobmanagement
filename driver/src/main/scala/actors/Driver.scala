package actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Address, Props, Terminated}
import common.{ArcJob, ArcJobRequest, Identifiers, Utils}

import scala.collection.mutable


object Driver {
  def apply(): Props = Props(new Driver)
  case class JobManagerInit(job: ArcJob, rmAddr: Address)
}

class Driver extends Actor with ActorLogging {
  import ClusterListener._
  import Driver._

  // Just a single resourcemananger for now
  var resourceManager = None: Option[Address]
  var jobManagers = mutable.IndexedSeq.empty[ActorRef]
  var jobManagerId: Long = 0 // unique id for each jobmanager that is created

  def receive = {
    case RmRegistration(rm) =>
      resourceManager = Some(rm)
      // test req
      val testJob = ArcJob(UUID.randomUUID().toString, Utils.testResourceProfile())
      val jobRequest = ArcJobRequest(testJob)
      self ! jobRequest
    case UnreachableRm(rm) =>
      // Foreach active JobManager, notify status
      resourceManager = None
    case RmRemoved(rm) =>
      resourceManager = None
    case ArcJobRequest(job) =>
      // The driver has received a job request from somewhere
      // whether it is through another actor, rest, or rpc...
      resourceManager match {
        case Some(rm) =>
          // Rm is availalble, create a jobmanager to deal with the job
          // Create and add jobManager to jobManagers
          val jobManager = context.actorOf(JobManager(), Identifiers.JOB_MANAGER+jobManagerId)
          jobManagerId +=1
          jobManagers = jobManagers :+ jobManager

          // Enable DeathWatch
          context watch jobManager

          // Send the job to the JobManager actor and be done with it
          jobManager ! JobManagerInit(job, rm)
        case None =>
          sender() ! "noresourcemanager" // change this..
      }
    case Terminated(ref) =>
      // JobManager was terminated somehow
      jobManagers = jobManagers.filterNot(_ == ref)
    case _ =>
  }

}
