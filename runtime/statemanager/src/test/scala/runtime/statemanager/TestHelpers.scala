package runtime.statemanager

import runtime.protobuf.messages.{ArcApp, ArcTask, ResourceProfile}

trait TestHelpers {
  val testArcApp = ArcApp("test", Seq(ArcTask("", 1, 1024, "")))
}
