package runtime.taskmanager.utils

import com.typesafe.scalalogging.LazyLogging
import org.hyperic.sigar.{ProcCpu, ProcMem, Sigar}

import scala.util.{Failure, Success, Try}

class ExecutorStats(pid: Long, sigar: Sigar) {


  def state(): String =
    sigar.getProcState(pid).toString


  def cpu(): Cpu =
    Cpu(sigar.getProcCpu(pid))

  case class Cpu(sys: Long,
                 user: Long,
                 total: Long,
                 start: Long,
                 last: Long,
                 percent: Double)

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

  def mem(): Mem =
    Mem(sigar.getProcMem(pid))

  case class Mem(size: Long,
                 pageFaults: Long,
                 share: Long,
                 minorFaults: Long,
                 majorFaults: Long)


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
}

object ExecutorStats extends LazyLogging {

  def apply(pid: Long): Option[ExecutorStats] = {
    Try {
      val sigar = new Sigar
      sigar.getCpuPerc // To force linkage error if there are any..

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
