package runtime.statemanager.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import runtime.ActorSpec
import runtime.common.messages._


class StateMasterSpec extends TestKit(ActorSystem("StateMasterSpec"))
  with ImplicitSender with ActorSpec {

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

      val task = WeldTask("test_task", " ", " ")
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
