package runtime.common

import akka.actor.{ActorRef, Address}

object Types {
  type TaskManagerAddr = Address
  type TaskManagerRef = ActorRef

  type JobManagerRef = ActorRef
  type JobManagerAddr = Address

  type BinaryExecutorRef = ActorRef
  type BinaryExecutorAddr = Address

  type BinaryManagerRef = ActorRef
  type BinaryManagerAddr = Address

  type BinaryReceiverRef = ActorRef
  type BinaryReceiverAddr = Address

  type ResourceManagerRef = ActorRef
  type ResourceManagerAddr = Address

  type SlotManagerRef = ActorRef
  type SlotManagerAddr = Address
}
