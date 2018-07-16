package runtime.appmanager

import runtime.protobuf.messages.{ArcJob, ArcProfile, ArcTask}


trait TestHelpers {
  val arcProfile = ArcProfile(1.0, 2000)
  val testArcJob = ArcJob("test", arcProfile, Seq(ArcTask("", "")))
}
