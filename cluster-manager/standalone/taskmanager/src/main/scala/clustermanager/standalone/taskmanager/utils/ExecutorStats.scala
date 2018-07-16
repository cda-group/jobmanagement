package clustermanager.standalone.taskmanager.utils

import com.typesafe.scalalogging.LazyLogging
import org.hyperic.sigar.{Cpu => _, Mem => _, _}
import runtime.protobuf.messages.{Cpu, Mem, _}

import scala.util.{Failure, Success, Try}

/** ExecutorStats fetches CPU, MEM, IO and general
  * process state metrics from process @pid.
  * Sigar has to be loaded for this class to function.
  * @param pid process PID
  * @param sigar Sigar object
  * @param bin path to the binary
  * @param addr which Akka Address the task is runing on
  */
class ExecutorStats(pid: Long, sigar: Sigar, bin: String, addr: String) {

  /** Collects metrics from the Executor and returns
    * an ExecutorMetric object on success, else a Throwable
    * @return Either[ExecutorMetric, Throwable]
    */
  def complete(): Either[ExecutorMetric, Throwable] = {
    try {
      val ts = System.currentTimeMillis()
      Left(ExecutorMetric(ts, state(), cpu(), mem(), io(), Executor(bin, addr)))
    } catch {
      case e: SigarException =>  Right(e)
      case l: LinkageError => Right(l)
      case o: Exception => Right(o)
    }
  }

  private def state(): ProcessState =
    ProcessState(sigar.getProcState(pid))

  private object ProcessState {
    def apply(p: ProcState): ProcessState = {
      val threads = p.getThreads
      val priority = p.getPriority
      val state = p.getState match {
        case ProcState.IDLE => "Idle"
        case ProcState.RUN => "Running"
        case ProcState.STOP => "Suspended"
        case ProcState.ZOMBIE => "Zombie"
        case ProcState.SLEEP =>  "Sleeping"
        case _ => "Unknown"
      }
      new ProcessState(threads, priority, state)
    }
  }

  private def cpu(): Cpu =
    Cpu(sigar.getProcCpu(pid))

  private object Cpu {
    def apply(p: ProcCpu): Cpu =  {
      val sys = p.getSys
      val user = p.getUser
      val total = p.getTotal
      val start = p.getStartTime
      val last = p.getLastTime
      val percent = p.getPercent
      new Cpu(sys, user, total, start, last, percent)
    }
  }

  private def mem(): Mem =
    Mem(sigar.getProcMem(pid))

  private object Mem {
    def apply(p: ProcMem): Mem = {
      val size = p.getSize
      val pageFaults = p.getPageFaults
      val share = p.getShare
      val minorFaults = p.getMinorFaults
      val majorFaults = p.getMajorFaults
      new Mem(size, pageFaults, share, minorFaults, majorFaults)
    }
  }

  private def io(): IO =
    IO(sigar.getProcDiskIO(pid))

  private object IO {
    def apply(p: ProcDiskIO): IO = {
      val read = p.getBytesRead
      val written = p.getBytesWritten
      val total = p.getBytesTotal
      new IO(read, written, total)
    }
  }

}

object ExecutorStats extends LazyLogging {

  def apply(pid: Long, bin: String, addr: String): Option[ExecutorStats] = {
    Try {
      val sigar = new Sigar
      sigar.getCpuPerc // To force linkage error if there is any
      println(System.getProperty("java.library.path"))
      sigar
    } match {
      case Success(sigar) =>
        Some(new ExecutorStats(pid, sigar, bin, addr))
      case Failure(e) =>
        logger.error("Could not load Sigar")
        None
    }
  }
}
