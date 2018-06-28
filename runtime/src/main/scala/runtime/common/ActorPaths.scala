package runtime.common

import akka.actor.{ActorPath, Address, RootActorPath}
import runtime.common.Identifiers._

object ActorPaths {

  def resourceManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER

  def slotManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER / SLOT_MANAGER

  def taskManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / TASK_MANAGER

  def driver(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / DRIVER
}
