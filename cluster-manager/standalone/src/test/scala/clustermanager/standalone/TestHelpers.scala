package clustermanager.standalone

import runtime.protobuf.messages.{ArcJob, ArcTask, ResourceProfile}

trait TestHelpers {
  val arcProfile = ResourceProfile(1, 2000)
  val testArcJob = ArcJob("test", Seq(ArcTask("", 1, 1024, "")))

  import kamon.sigar.SigarProvisioner
  SigarProvisioner.provision()
}
