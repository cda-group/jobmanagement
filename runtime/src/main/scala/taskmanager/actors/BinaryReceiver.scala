package taskmanager.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.Tcp.{PeerClosed, Received}
import akka.util.ByteString
import taskmanager.utils.ExecutionEnvironment


object BinaryReceiver {
  def apply(id: Int, env: ExecutionEnvironment): Props =
    Props(new BinaryReceiver(id, env))
}

/** Actor that receives a Rust binary and writes it to file
  *
  * BinaryReceiver is registered by Akka IO TCP to
  * handle a client connection. When the transfer is complete
  * the BinaryReceiver will receive a BinaryUploaded event
  * and write the binary to file in the specified Execution
  * Environment
  */
class BinaryReceiver(id: Int, env: ExecutionEnvironment)
  extends Actor with ActorLogging {

  import BinaryManager._

  var storage = Vector.empty[ByteString]

  def receive = {
    case Received(data) =>
      storage = storage :+ data
    case PeerClosed =>
      log.info("Peer closed for: " + id)
      context stop self
    case BinaryUploaded =>
      val arrByte = storage.map(_.toArray)
        .toArray
        .flatten
      if (env.writeBinaryToFile(id, arrByte)) {
        sender() ! BinaryReady(id)
      } else {
        sender() ! BinaryWriteFailure
      }
      context stop self
  }
}
