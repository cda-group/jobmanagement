package runtime.common

import akka.actor.{ActorRef, Address}

object Types {
  type TaskManagerAddr = Address
  type TaskManagerRef = ActorRef

  type AppMasterRef = ActorRef
  type AppMasterAddr = Address

  type TaskExecutorRef = ActorRef
  type TaskExecutorAddr = Address

  type TaskMasterRef = ActorRef
  type TaskMasterAddr = Address

  type TaskReceiverRef = ActorRef
  type TaskReceiverAddr = Address

  type ResourceManagerRef = ActorRef
  type ResourceManagerAddr = Address

  type SlotManagerRef = ActorRef
  type SlotManagerAddr = Address

  type StateManagerRef = ActorRef
  type StateManagerAddr = Address
}
