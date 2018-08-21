package runtime.appmanager.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import runtime.protobuf.messages.{TaskTransferAck, TaskTransferComplete}

object TaskSender {
  def apply(server: InetSocketAddress, bytes: Array[Byte], tm: ActorRef, taskName: String): Props =
    Props(new TaskSender(server, bytes, tm, taskName))
}

/** Actor that transfers tasks (binaries) over TCP.
  *
  * @constructor initialize actor with address, bytes and ref to TaskMaster
  * @param server InetSocketAddress to target TaskManager
  * @param bytes The actual binary to be transferred
  * @param tm ActorRef to TaskManager's TaskMaster
  * @param taskName name of the Task
  */
class TaskSender(server: InetSocketAddress, bytes: Array[Byte], tm: ActorRef, taskName: String)
  extends Actor with ActorLogging {
  import context.system // Required for TCP IO

  import runtime.protobuf.ProtoConversions.InetAddr._

  IO(Tcp) ! Connect(server)


  def receive = {
    case CommandFailed(_ : Connect) =>
      log.error(s"TaskSender failed to establish connection to ${server.toString}")
      context stop self
    case c@Connected(remote, local) =>
      val conn = sender()
      conn ! Register(self)
      val byteString = ByteString.fromArray(bytes)
      conn ! Tcp.Write(byteString, TaskTransferAck(local))
    case TaskTransferAck(inet) =>
      tm ! TaskTransferComplete(inet, taskName)
    case _=>
  }
}
