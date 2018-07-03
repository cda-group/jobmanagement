package runtime.appmanager.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import runtime.common.{BinaryTransferAck, BinaryTransferComplete}

object BinarySender {
  def apply(server: InetSocketAddress, bytes: Array[Byte], bm: ActorRef): Props =
    Props(new BinarySender(server, bytes, bm))
}

/** Actor that transfers binaries over TCP.
  *
  * @constructor initialize actor with address, bytes and ref to BinaryManager.
  * @param server InetSocketAddress to target TaskManager
  * @param bytes The actual binary to be transferred
  * @param bm ActorRef to TaskManager's BinaryManager
  */
class BinarySender(server: InetSocketAddress, bytes: Array[Byte], bm: ActorRef)
  extends Actor with ActorLogging {
  import context.system // Required for TCP IO

  IO(Tcp) ! Connect(server)

  def receive = {
    case CommandFailed(_ : Connect) =>
      context stop self
    case c@Connected(remote, local) =>
      val conn = sender()
      conn ! Register(self)
      val byteString = ByteString.fromArray(bytes)
      conn ! Tcp.Write(byteString, BinaryTransferAck(local))
    case BinaryTransferAck(inet) =>
      bm ! BinaryTransferComplete(inet)
    case _=>
  }
}
