package runtime

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import runtime.common.{ArcJob, Utils, WeldJob, WeldTask}


trait ActorSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  val testArcJob = ArcJob("test", Utils.testResourceProfile(), WeldJob(Seq(WeldTask("", ""))))
}
