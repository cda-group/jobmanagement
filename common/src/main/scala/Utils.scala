package common

import akka.actor.{ActorPath, Address, RootActorPath}


object Utils {

  def jobmasterPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "resourcemanager" / "jobmaster"

  def workerPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "worker" / "handler"

  def driverPath(member: Address): ActorPath =
    RootActorPath(member) / "user" / "driver" / "handler"

}
