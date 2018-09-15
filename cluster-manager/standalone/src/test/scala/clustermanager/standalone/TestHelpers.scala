package clustermanager.standalone

import runtime.protobuf.messages.{ArcApp, ArcTask}

trait TestHelpers {
  val testArcApp = ArcApp("test", Seq(ArcTask("", 1, 1024, "")))

  import kamon.sigar.SigarProvisioner
  SigarProvisioner.provision()
}
