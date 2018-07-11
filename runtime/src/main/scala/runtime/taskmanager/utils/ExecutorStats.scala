package runtime.taskmanager.utils

import com.typesafe.scalalogging.LazyLogging
import org.hyperic.sigar.{Cpu => _, Mem => _, _}
import runtime.common.messages._

import scala.util.{Failure, Success, Try}

class ExecutorStats(pid: Long, sigar: Sigar) extends LazyLogging {

  /** Collects metrics from the Executor and returns
    * an ExecutorMetric object on success, else a Throwable
    * @return Either[ExecutorMetric, Throwable]
    */
  def complete(): Either[ExecutorMetric, Throwable] = {
    try {
      Left(ExecutorMetric(state(), cpu(), mem(), io()))
    } catch {
      case e: SigarException =>  Right(e)
      case l: LinkageError => Right(l)
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

  def apply(pid: Long): Option[ExecutorStats] = {
    Try {
      val sigar = new Sigar
      sigar.getCpuPerc // To force linkage error if there is any

      sigar
    } match {
      case Success(sigar) =>
        Some(new ExecutorStats(pid, sigar))
      case Failure(e) =>
        logger.error("Could not load Sigar")
        None
    }
  }
}
