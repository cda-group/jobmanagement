package clustermanager.standalone.taskmanager.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.Tcp.{PeerClosed, Received}
import akka.util.ByteString
import clustermanager.common.executor.ExecutionEnvironment


private[standalone] object TaskReceiver {
  def apply(env: ExecutionEnvironment): Props =
    Props(new TaskReceiver(env))
}

/** Actor that receives a binary and writes it to file
  *
  * TaskReceiver is registered by Akka IO TCP to
  * handle a client connection. When the transfer is complete
  * the TaskReceiver will receive a TaskUploaded event
  * and write the binary to file in the specified Execution
  * Environment
  */
private[standalone] class TaskReceiver(env: ExecutionEnvironment)
  extends Actor with ActorLogging {

  import TaskMaster._

  private var buffer = ByteString.empty

  def receive = {
    case Received(data) =>
      buffer = buffer ++ data
    case PeerClosed =>
      context stop self
    case TaskUploaded(name) =>
      if (env.writeBinaryToFile(name, buffer.toArray)) {
        sender() ! TaskReady(name)
      } else {
        sender() ! TaskWriteFailure(name)
      }
      context stop self
  }
}
