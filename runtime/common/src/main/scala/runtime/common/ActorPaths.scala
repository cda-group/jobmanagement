package runtime.common

import Identifiers._
import akka.actor.{ActorPath, Address, RootActorPath}

object ActorPaths {

  def resourceManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER

  def slotManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / RESOURCE_MANAGER / SLOT_MANAGER

  def taskManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / TASK_MANAGER

  def appManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / APP_MANAGER

  def stateManager(member: Address): ActorPath =
    RootActorPath(member) / USER / LISTENER / STATE_MANAGER
}
