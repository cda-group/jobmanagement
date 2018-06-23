package common

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.io.Tcp.Event


case class ArcJob(id: String, profile: ArcProfile, jobManagerRef: Option[ActorRef] = None)
case class ArcJobRequest(job: ArcJob)
case class ArcProfile(cpuCores: Double, memoryInMB: Long) {
  def matches(other: ArcProfile): Boolean =
    this.cpuCores >= other.cpuCores && this.memoryInMB >= other.memoryInMB
}



case object BMHeartBeat
case class BinaryJob(binaries: Seq[Array[Byte]])
case object BinariesCompiled
case class BinaryTransferConn(inet: InetSocketAddress)
case object BinaryTransferError
case class BinaryTransferAck(inet: InetSocketAddress) extends Event
case class BinaryTransferComplete(inet: InetSocketAddress)


// TaskManager
sealed trait AllocateResponse
case class AllocateSuccess(job: ArcJob, ref: ActorRef) extends AllocateResponse
case class AllocateFailure(resp: SlotRequestResp) extends AllocateResponse
case class AllocateError(err:  String) extends AllocateResponse

case object TaskManagerInit
case class Allocate(job: ArcJob, slots: Seq[TaskSlot])
case class ReleaseSlots(slotIndxes: Seq[Int])
case class SlotUpdate(slots: Seq[TaskSlot])
case class BinaryManagerInit()

case class TaskSlot(index: Int, profile: ArcProfile, state: SlotState = Free) {
  def newState(s: SlotState): TaskSlot = {
    this.copy(state = s)
  }
}


