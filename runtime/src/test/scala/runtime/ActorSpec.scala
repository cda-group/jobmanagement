package runtime

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import runtime.common.Utils
import runtime.common.messages.{ArcJob, ArcTask}


trait ActorSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  val testArcJob = ArcJob("test", Utils.testResourceProfile(), Seq(ArcTask("", "")))
}
