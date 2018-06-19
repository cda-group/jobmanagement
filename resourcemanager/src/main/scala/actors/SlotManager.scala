package actors

import actors.ResourceManager.SlotRequest
import akka.actor.{Actor, ActorLogging, Address, Props}
import common._

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object SlotManager {
  def apply(): Props = Props(new SlotManager)
}

/**
  * SlotManager is responsible for handling the TaskSlots of all
  * registered TaskManagers.
  */
class SlotManager extends Actor with ActorLogging {
  import ClusterListener._

  var taskManagers = mutable.IndexedSeq.empty[Address]
  val slots = mutable.HashMap[Address, Seq[TaskSlot]]()
  var roundNumber = 0


  def receive = {
    case TaskManagerRegistration(tm) if !taskManagers.contains(tm) =>
      taskManagers = taskManagers :+ tm
      val target = context.actorSelection(ActorPaths.taskManager(tm))
      // TODO: add retry logic in case worker is not reachable
      // in order to make sure that the TaskManager is initialized
      target ! TaskManagerInit
    case TaskManagerRemoved(tm) =>
      cleanTaskManager(tm)
    case UnreachableTaskManager(tm) =>
      cleanTaskManager(tm)
    case SlotUpdate(s) =>
      slots.put(sender().path.address, s)
      log.info(slots.toString())
    case req@SlotRequest(job) =>
      //TODO: Clean and improve
      handleSlotRequest(req) match {
        case NoSlotsAvailable =>
          log.info("No Slots Available")
        case NoTaskManagersAvailable =>
          log.info("No Task Managers Available")
        case SlotAvailable(taskSlot, addr) =>
          log.info("Slots Available")
          val taskManager = context.actorSelection(ActorPaths.taskManager(addr))
          // wrapping it in a Seq for now until handleSlotRequest is fixed.
          taskManager forward Allocate(job, Seq(taskSlot))
      }
    case _ =>
  }

  private def cleanTaskManager(tm: Address): Unit = {
    Try {
      taskManagers.filterNot(_ == tm)
      slots.remove(tm)
    } match {
      case Success(_) => // ignore
      case Failure(e) => log.error("Error while cleaning TaskManager")
    }
  }

  //TODO: Improve..
  // Only handles 1 possible round
  // Fix so more than 1 slot can be allocated.
  // Perhaps 1 job requires multiple taskslots
  private def handleSlotRequest(req: SlotRequest): SlotRequestResp = {
    if (roundNumber > taskManagers.size)
      roundNumber = 0

    if (taskManagers.nonEmpty) {
      // Safety check
      if (roundNumber <= taskManagers.size) {
        // Find a free slot from the taskManager(roundNumber)
        val result = slots.get(taskManagers(roundNumber)) match {
          case Some(set) =>
            val freeSlots = set.filter(slot => slot.state == Free && slot.profile.matches(req.job.profile))
            if (freeSlots.nonEmpty)
              SlotAvailable(randomSlot(freeSlots), taskManagers(roundNumber))
            else
              NoSlotsAvailable
          case None =>
            log.error("")
            NoSlotsAvailable
        }
        roundNumber += 1
        result
      } else {
        roundNumber = 0
        NoSlotsAvailable
      }
    } else {
      NoTaskManagersAvailable
    }
  }

  def randomSlot[T](s: Seq[T]): T = {
    val n = util.Random.nextInt(s.size)
    s.iterator.drop(n).next
  }
}
