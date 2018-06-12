package common

import akka.actor.{ActorPath, Address, RootActorPath}


sealed trait SlotState
case object Allocated extends SlotState
case object Free extends SlotState
case object Active extends SlotState
case object Realising extends SlotState

object Utils {

  def slotManagerPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "listener" / "slotmanager"

  def taskManagerPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "listener" / "taskmanager" / "handler"

  def driverPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "listener" / "driver" / "handler"

}
