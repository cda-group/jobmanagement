package runtime

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import runtime.common.Utils
import runtime.common.messages.{ArcJob, WeldJob, WeldTask}


trait ActorSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  val testArcJob = ArcJob("test", Utils.testResourceProfile(), WeldJob(Seq(WeldTask("", ""))))
}
