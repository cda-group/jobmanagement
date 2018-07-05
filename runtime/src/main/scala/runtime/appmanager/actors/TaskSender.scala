package runtime.appmanager.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import runtime.common.models.{TaskTransferAck, TaskTransferComplete}
import runtime.common.ProtoConversions

object TaskSender {
  def apply(server: InetSocketAddress, bytes: Array[Byte], tm: ActorRef): Props =
    Props(new TaskSender(server, bytes, tm))
}

/** Actor that transfers tasks (binaries) over TCP.
  *
  * @constructor initialize actor with address, bytes and ref to TaskMaster
  * @param server InetSocketAddress to target TaskManager
  * @param bytes The actual binary to be transferred
  * @param tm ActorRef to TaskManager's TaskMaster
  */
class TaskSender(server: InetSocketAddress, bytes: Array[Byte], tm: ActorRef)
  extends Actor with ActorLogging {
  import context.system // Required for TCP IO

  import ProtoConversions.InetAddr._

  IO(Tcp) ! Connect(server)

  def receive = {
    case CommandFailed(_ : Connect) =>
      context stop self
    case c@Connected(remote, local) =>
      val conn = sender()
      conn ! Register(self)
      val byteString = ByteString.fromArray(bytes)
      conn ! Tcp.Write(byteString, TaskTransferAck(local))
    case TaskTransferAck(inet) =>
      tm ! TaskTransferComplete(inet)
    case _=>
  }
}
