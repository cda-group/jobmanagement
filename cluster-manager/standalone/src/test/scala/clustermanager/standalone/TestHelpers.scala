package clustermanager.standalone

import runtime.protobuf.messages.{ArcJob, ArcTask, ResourceProfile}

trait TestHelpers {
<<<<<<< HEAD
  val arcProfile = ResourceProfile(1, 2000)
=======
>>>>>>> 383ddc6f393cf1d3f6818806cafee905261180b0
  val testArcJob = ArcJob("test", Seq(ArcTask("", 1, 1024, "")))

  import kamon.sigar.SigarProvisioner
  SigarProvisioner.provision()
}
