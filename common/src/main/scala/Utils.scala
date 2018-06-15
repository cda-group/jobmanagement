package common

import akka.actor.Address

sealed trait SlotState
case object Allocated extends SlotState
case object Free extends SlotState
case object Active extends SlotState
case object Realising extends SlotState

sealed trait SlotRequestResp
case object NoTaskManagersAvailable extends SlotRequestResp
case object NoSlotsAvailable extends SlotRequestResp
case class SlotAvailable(taskSlot: TaskSlot, addr: Address) extends SlotRequestResp

object Utils {

  // For development
  def testResourceProfile(): ArcProfile =
    ArcProfile(1.0, 1000) // 1.0 cpu core & 1000MB mem

}
