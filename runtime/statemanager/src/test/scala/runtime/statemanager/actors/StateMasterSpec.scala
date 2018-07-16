package runtime.statemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import runtime.common.ActorSpec
import runtime.protobuf.messages._
import runtime.statemanager.TestHelpers


object StateMasterSpec {
  val actorSystem = ActorSystem("StateMasterSpec", ConfigFactory.parseString(
    """
      | akka.actor.provider = cluster
      | akka.loggers = ["akka.testkit.TestEventListener"]
      | akka.stdout-loglevel = "OFF"
      | akka.loglevel = "OFF"
    """.stripMargin))
}

class StateMasterSpec extends TestKit(StateMasterSpec.actorSystem)
  with ImplicitSender with ActorSpec with TestHelpers {


  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A StateMaster Actor" must {

    "Notify AppMaster of its Ref" in {
      val appMaster = TestProbe()
      val probe = TestProbe()
      val master = system.actorOf(StateMaster(appMaster.ref, testArcJob))
      appMaster.expectMsgType[StateMasterConn]
    }

    "Retrieve and Report metrics" in {
      val appMaster = TestProbe()
      val probe = TestProbe()
      val master = system.actorOf(StateMaster(appMaster.ref, testArcJob))

      val task = ArcTask("test_task", " ", " ")
      val fakeMetric = ExecutorMetric(
        System.currentTimeMillis(),
        ProcessState(1, 20, "Running"),
        Cpu(1,1,1,1,1,1.0),
        Mem(1,1,1,1),
        IO(0,0,0)
      )

      val metric = ArcTaskMetric(task, fakeMetric)
      master ! metric

      master ! ArcJobMetricRequest(testArcJob.id)
      val report = expectMsgType[ArcJobMetricReport]

      report.jobId shouldBe testArcJob.id
      report.metrics.size === 1
      report.metrics.head.task.name shouldBe "test_task"
      report.metrics.head.executorMetric.timestamp < System.currentTimeMillis()
    }
  }

}
