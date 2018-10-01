package runtime.tests

import runtime.protobuf.messages.{ArcJob, ArcProfile, ArcTask}


trait RuntimeHelper {
  val bigProfile = ArcProfile(8.0, 16000)
  val mediumProfile  = ArcProfile(4.0, 6000)
  val smallProfile = ArcProfile(1.0, 1000)

  val tempTasks  = Seq(ArcTask("", 1, 1024, ""))

  val smallJob = ArcJob("smalljob", smallProfile, tempTasks)
  val mediumJob = ArcJob("mediumjob", mediumProfile, tempTasks)
  val tooBigJob = ArcJob("bigjob", bigProfile, tempTasks)
}
