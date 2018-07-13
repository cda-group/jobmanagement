package runtime.taskmanager.utils

import org.hyperic.sigar.Sigar
import runtime.BaseSpec

class ExecutorStatsSpec extends BaseSpec {

  "ExecutorStats" should "react to bad pid" in {
    val execStats = ExecutorStats(-1000, "", "")
    execStats.map(_.complete()) map {
      case Left(e) => fail
      case Right(r) => succeed
    }
  }

  "ExecutorStats" should "report real metrics" in {
    val pid = new Sigar().getPid // Get pid of this process
    val execStats = ExecutorStats(pid, "", "")
    execStats.map(_.complete()) map {
      case Left(e) => succeed
      case Right(r) => fail
    }
  }


}
