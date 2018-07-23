package clustermanager.yarn.taskexecutor

import java.io.{BufferedReader, InputStreamReader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import runtime.protobuf.messages.{ArcTask, ArcTaskUpdate}


object TaskExecutorReader {
  def apply(p: Process, appMaster: ActorRef, task: ArcTask): Props =
    Props(new TaskExecutorReader(p, appMaster, task))
  case class Result(s: String)
}


/**
  * PoC actor just for handling weld/arc results to StdOut
  * @param proc Java Process
  */
class TaskExecutorReader(proc: Process, appMaster: ActorRef, task: ArcTask)
  extends Actor with ActorLogging {
  import TaskExecutorReader._

  override def preStart(): Unit = {
    val reader = new BufferedReader(new InputStreamReader(proc.getInputStream))

    var line: String = null
    var res: String = ""
    import scala.util.control.Breaks._

    breakable {
      while ({line = reader.readLine; line != null}) {
        res = line
        println(line)
        break
      }
    }

    self ! Result(res)
  }

  def receive = {
    case Result(s) =>
      val updated = task.copy(result = Some(s))
      log.info("My parent is: " + context.parent)
      context.parent ! ArcTaskUpdate(updated)
      // We are done
      context stop self
    case _ =>
  }
}
