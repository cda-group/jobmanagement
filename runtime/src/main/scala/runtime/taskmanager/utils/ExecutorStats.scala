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
    def apply(procCpu: ProcCpu): Cpu =  {
      val sys = procCpu.getSys
      val user = procCpu.getUser
      val total = procCpu.getTotal
      val start = procCpu.getStartTime
      val last = procCpu.getLastTime
      val percent = procCpu.getPercent
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
    def apply(procMem: ProcMem): Mem = {
      val size = procMem.getSize
      val pageFaults = procMem.getPageFaults
      val share = procMem.getShare
      val minorFaults = procMem.getMinorFaults
      val majorFaults = procMem.getMajorFaults
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
