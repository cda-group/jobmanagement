package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import common.{HeartbeatInit, Test, WorkerState}

import scala.concurrent.duration._
// For now..
import scala.concurrent.ExecutionContext.Implicits.global


object Worker {
  def apply(): Props = Props(new Worker())
}

class Worker extends Actor with ActorLogging {
  val cluster = Cluster(context.system)


  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  log.info("I AM WORKER: " + self)

  def receive = {
    case HeartbeatInit =>
      log.info("Received HeartbeatInit")
      /*
      val cancellable = context.system.scheduler.schedule(
        0 milliseconds,
        200 milliseconds,
        sender(),
        WorkerState(1,2)
      )
      */
    case Test => println("I got a test from: " + sender())
    case MemberUp(m) =>
      println("Member up: " + m)
    case _ => println("")
  }
}
