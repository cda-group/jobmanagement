package clustermanager.standalone.taskmanager.utils

import akka.cluster.metrics.SigarProvider
import kamon.sigar.SigarProvisioner
import org.hyperic.sigar.Sigar
import org.scalatest.BeforeAndAfterAll
import runtime.common.BaseSpec

class ExecutorStatsSpec extends BaseSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    SigarProvisioner.provision()
  }

  override def afterAll(): Unit = {

  }

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
