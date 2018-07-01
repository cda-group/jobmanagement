package runtime

import runtime.common.{ArcJob, ArcProfile, WeldJob, WeldTask}

trait RuntimeHelper {
  val bigProfile = ArcProfile(8.0, 16000)
  val mediumProfile  = ArcProfile(4.0, 6000)
  val smallProfile = ArcProfile(1.0, 1000)

  val tempJob = WeldJob(Seq(WeldTask("", "")))

  val smallJob = ArcJob("smalljob", smallProfile, tempJob)
  val mediumJob = ArcJob("mediumjob", mediumProfile, tempJob)
  val tooBigJob = ArcJob("bigjob", bigProfile, tempJob)
}
