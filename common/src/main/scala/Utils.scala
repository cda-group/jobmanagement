package common

import akka.actor.{ActorPath, Address, RootActorPath}


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

  final val RESOURCE_MANAGER = "resourcemanager"
  final val LISTENER = "listener"
  final val SLOT_MANAGER = "slotmanager"
  final val TASK_MANAGER = "taskmanager"
  final val USER = "user"
  final val HANDLER = "handler"
  final val DRIVER = "driver"
  final val SLOT_HANDLER = "slothandler"

  def resourceManagerPath(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER

  def slotManagerPath(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER / SLOT_MANAGER

  def taskManagerPath(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / TASK_MANAGER

  def slotHandlerPath(index: Int, member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / TASK_MANAGER / (SLOT_HANDLER + index)

  def driverPath(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / DRIVER / HANDLER

  // For development
  def testResourceProfile(): ResourceProfile =
    ResourceProfile(1.0, 1000) // 1.0 cpu core & 1000MB mem

}
