package runtime.taskmanager.actors

import java.io.{BufferedReader, InputStreamReader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import runtime.common.messages.{WeldTask, WeldTaskCompleted}


object TaskExecutorReader {
  def apply(p: Process, appMaster: ActorRef, task: WeldTask): Props =
    Props(new TaskExecutorReader(p, appMaster, task))
}


/**
  * PoC actor just for handling weld/arc results to StdOut
  * @param proc Java Process
  */
class TaskExecutorReader(proc: Process, appMaster: ActorRef, task: WeldTask)
  extends Actor with ActorLogging {

  override def preStart(): Unit = {
    val reader = new BufferedReader(new InputStreamReader(proc.getInputStream))

    var line: String = null
    var res: String = ""
    while ({line = reader.readLine; line != null}) {
      res = line
      println(line)
    }

    val updated = task.copy(result = Some(res))
    appMaster ! WeldTaskCompleted(updated)

    // We are done
    context stop self
  }

  def receive = {
    case _ =>
  }
}
