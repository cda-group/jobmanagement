package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.Tcp.{PeerClosed, Received}
import akka.util.ByteString
import clustermanager.common.executor.ExecutionEnvironment


object TaskReceiver {
  def apply(id: String, env: ExecutionEnvironment): Props =
    Props(new TaskReceiver(id, env))
}

/** Actor that receives a binary and writes it to file
  *
  * TaskReceiver is registered by Akka IO TCP to
  * handle a client connection. When the transfer is complete
  * the TaskReceiver will receive a TaskUploaded event
  * and write the binary to file in the specified Execution
  * Environment
  */
class TaskReceiver(id: String, env: ExecutionEnvironment)
  extends Actor with ActorLogging {

  import TaskMaster._

  private var buffer = ByteString.empty

  def receive = {
    case Received(data) =>
      buffer = buffer ++ data
    case PeerClosed =>
      context stop self
    case TaskUploaded =>
      if (env.writeBinaryToFile(id, buffer.toArray)) {
        sender() ! TaskReady(id)
      } else {
        sender() ! TaskWriteFailure
      }
      context stop self
  }
}
