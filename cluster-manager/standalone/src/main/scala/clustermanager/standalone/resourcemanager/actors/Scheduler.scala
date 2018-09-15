package clustermanager.standalone.resourcemanager.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Cancellable, Props}
import clustermanager.standalone.resourcemanager.actors.ResourceManager.ResourceRequest
import runtime.common.{ActorPaths, IdGenerator}
import runtime.protobuf.messages.SliceState.ALLOCATED
import runtime.protobuf.messages._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}


/**
  * SliceManager is responsible for handling the ContainerSlice's of all
  * registered TaskManagers.
  */
private[resourcemanager] abstract class SliceManager extends Actor with ActorLogging {
  import ClusterListener._

  protected var taskManagers = IndexedSeq.empty[Address]
  protected val slices = mutable.HashMap.empty[Address, Seq[ContainerSlice]]
  protected val offeredSlices = mutable.HashSet.empty[Seq[ContainerSlice]]


  def receive = {
    case TaskManagerRegistration(tm) if !taskManagers.contains(tm) =>
      taskManagers = taskManagers :+ tm
      val target = context.actorSelection(ActorPaths.taskManager(tm))
      // TODO: add retry logic in case worker is not reachable
      // in order to make sure that the TaskManager is initialized
      target ! TaskManagerInit()
    case TaskManagerRemoved(tm) =>
      log.info(s"TaskManager Removed: $tm" )
      cleanTaskManager(tm)
    case UnreachableTaskManager(tm) =>
      log.info(s"TaskManager Unreachable: $tm")
      cleanTaskManager(tm)
    case SliceUpdate(_slices) =>
      slices.put(sender().path.address, _slices)
  }


  private def cleanTaskManager(tm: Address): Unit = {
    Try {
      taskManagers = taskManagers.filterNot(_ == tm)
      val slicesOpt = slices.get(tm)
      slicesOpt match {
        case Some(s) =>
          // If they exist in offeredSlices, then remove them
          offeredSlices.remove(s)
        case None =>  // Ignore
      }
      // Finish cleanup
      slices.remove(tm)
    } match {
      case Success(_) => // ignore
      case Failure(e) => log.error("Error while cleaning TaskManager")
    }
  }
}


/** To make Schedulers pluggable, each Scheduler
  * extends this Abstract scheduler which provides a SliceManager
  */
private[resourcemanager] abstract class Scheduler extends SliceManager


object RoundRobinScheduler {
  case object ScheduleTick
}

/** RoundRobinScheduler schedules jobs onto TaskManagers in
  * a Round Robin fashion. Locality preferences are also taken into
  * consideration.
  *
  * Note: This is currently just a simple Proof of Concept
  * scheduler, i.e., it is not the best.
  */
private[resourcemanager] class RoundRobinScheduler extends Scheduler {
  import RoundRobinScheduler._

  // Handles implicit conversions of ActorRef and ActorRefProto
  implicit val sys: ActorSystem = context.system
  import runtime.protobuf.ProtoConversions.ActorRef._
  import runtime.protobuf.ProtoConversions.Address._

  // Dequeue order based on the app's priority value.
  // The higher Integer, the higher priority the job has.
  private def appOrder(a: ArcApp) = a.priority

  private val appQueue: mutable.PriorityQueue[ArcApp] =
    mutable.PriorityQueue.empty[ArcApp](Ordering.by(appOrder))

  private var scheduleTicker = None: Option[Cancellable]

  import scala.concurrent.duration._
  import context.dispatcher

  private var roundNumber = 0

  override def preStart(): Unit =
    scheduleTicker = startScheduleTicker(self)

  override def postStop(): Unit =
    scheduleTicker.map(_.cancel())

  override def receive = super.receive orElse {
    case ResourceRequest(app) =>
      log.info(s"Adding app with id ${app.id} to the queue")
      appQueue.enqueue(app)
    case ScheduleTick if appQueue.nonEmpty && taskManagers.nonEmpty =>
      val app = appQueue.dequeue()
      val currentRound = roundNumber
      allocationAttempt(app) match {
        case Some(containers) =>
          containers.foreach { container  =>
            val tmAddr: Address = container.taskmanager
            val taskManager = context.actorSelection(ActorPaths.taskManager(tmAddr))
            offeredSlices.add(container.slices)
            taskManager ! ContainerAllocation(UUID.randomUUID().toString, container)
          }
         roundNumber = currentRound + 1
        case None =>
          log.info("Could not find any containers, requeing")
          appQueue.enqueue(app)
      }
    case SlicesAllocated(_slices) =>
      if (offeredSlices.remove(_slices))
        log.debug("Offered slices have now been allocated, removing")
      else
        log.error("Could not remove the offered slices")


      val addr = sender().path.address
      val current = slices.get(addr)

      current match {
        case Some(s) =>
          slices.update(addr, s intersect _slices.map(_.copy(state = ALLOCATED)))
        case None =>
        // Ignore
        }
  }

  private def startScheduleTicker(scheduler: ActorRef): Option[Cancellable] = {
    Some(context.
      system.scheduler.schedule(
      0.milliseconds,
      1000.milliseconds) {
      scheduler ! ScheduleTick
    })
  }

  private def allocationAttempt(app: ArcApp): Option[Seq[Container]] = {
    if (app.locality)
      withLocality(app, tries = 0, max = taskManagers.size)
    else
      noPreference(app)
  }

  @tailrec
  private def withLocality(app: ArcApp, tries: Int, max: Int): Option[Seq[Container]] = {
    if (tries == max) {
      None
    } else {

      if (roundNumber > taskManagers.size - 1)
        roundNumber = 0

      slices.get(taskManagers(roundNumber)) match {
        case Some(fSlices) =>
          // Free Slices on this TaskManager
          val freeSlices = fSlices.filter(s => s.state == SliceState.FREE)
          fetchSlots(freeSlices, app) match {
            case Some(chosen) =>
              import runtime.protobuf.ProtoConversions.Address._
              val c = Container(IdGenerator.container(), app.id, app.appMasterRef.get,
                taskManagers(roundNumber), chosen, app.tasks)
              Some(Seq(c))
            case None =>
              roundNumber += 1
              var _tries = tries + 1
              withLocality(app, _tries, max)
          }
        case None =>
          None
      }
    }
  }

  private def noPreference(app: ArcApp): Option[Seq[Container]] = {
    None
  }


  /** Checks if the free slices matches the resource profile of the job
    * @param freeSlices ContainerSlices
    * @param app ArcApp
    * @return Option of ContainerSlices
    */
  private def fetchSlots(freeSlices: Seq[ContainerSlice], app: ArcApp): Option[Seq[ContainerSlice]] = {
    val appProfile = buildProfile(app.tasks)

    val resources = freeSlices.foldLeft((ResourceProfile(0, 0), Seq[ContainerSlice]())) { (x, y) =>
      if (x._1.matches(appProfile) || offeredSlices.contains(Seq(y)))
        x
      else
        (x._1.copy(cpuCores = x._1.cpuCores + y.profile.cpuCores,
          memoryInMb = x._1.memoryInMb + y.profile.memoryInMb), x._2 :+ y)
    }

    if (resources._1.matches(appProfile))
      Some(resources._2)
    else
      None
  }

  private def buildProfile(tasks: Seq[ArcTask]): ResourceProfile = {
    val (memory, cores) = tasks.foldLeft(0,0) { (x,y) =>
      (x._1 + y.memory, x._2 + y.cores)
    }
    ResourceProfile(cores, memory)
  }
}

