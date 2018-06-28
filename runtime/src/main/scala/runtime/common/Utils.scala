package runtime.common

import akka.actor.Address

sealed trait SlotState
case object Allocated extends SlotState
case object Free extends SlotState
case object Active extends SlotState

sealed trait SlotRequestResp
case object NoTaskManagersAvailable extends SlotRequestResp
case object NoSlotsAvailable extends SlotRequestResp
case object UnexpectedError extends SlotRequestResp
case class SlotAvailable(taskSlot: Seq[TaskSlot], addr: Address) extends SlotRequestResp

object Utils {

  // For development
  def testResourceProfile(): ArcProfile =
    ArcProfile(2.0, 2000) // 2.0 cpu core & 2000MB mem

  def slotProfile(): ArcProfile =
    ArcProfile(1.0, 1000) // 1.0 cpu core & 1000MB mem
}
