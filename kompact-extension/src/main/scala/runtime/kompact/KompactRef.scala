package runtime.kompact

import akka.actor.{ActorRef, ActorSystem}
import io.netty.channel.ChannelHandlerContext

import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.concurrent.{Future => JFuture}

import akka.util.Timeout
import runtime.kompact.KompactAsk.{AskResponse, AskTickerInit}
import runtime.kompact.messages.{Ask, KompactAkkaMsg}


/** Commands that can be executed on KompactRef's
  */
private[kompact] trait KompactApi {

  /** Fire and forget message sending
    * @param msg Protobuf Message
    */
  def !(msg: KompactAkkaMsg): Unit

  /** Returns a Future awaiting a response from the executor
    * @param msg Protobuf Message
    * @return Future[AskResponse] i.e., AskSuccess/AskFailure
    */
  def ?(msg: KompactAkkaMsg)(implicit sys: ActorSystem,
                             ec: ExecutionContext,
                             t: Timeout): Future[AskResponse]


  /** Enable Akka Actors to DeathWatch KompactRef's
    * @param watcher ActorRef
    */
  def kompactWatch(watcher: ActorRef): Unit

  /** Close a connection to an Executor
    */
  def kill(): Unit

}


/** KompactRef represents a connection Kompact Connection
  * @param jobId  String
  * @param executorName String
  * @param akkaPath String
  * @param kompactPath String
  * @param ctx Netty ContextHandlerContext
  */
final case class KompactRef(jobId: String,
                            executorName: String,
                            akkaPath: String,
                            kompactPath: String,
                            ctx: ChannelHandlerContext
                           ) extends KompactApi {

  override def !(msg: KompactAkkaMsg): Unit = {
    if (ctx.channel().isWritable)
      ctx.writeAndFlush(msg)
  }

  override def ?(msg: KompactAkkaMsg)(implicit sys: ActorSystem,
                                      ec: ExecutionContext,
                                      t: Timeout): Future[AskResponse] = {
    val askActor = sys.actorOf(KompactAsk(t))
    // This might have to be toStringWithAddress..
    val askReq = Ask(askActor.path.toString, msg)
    val kMsg = KompactAkkaMsg().withAsk(askReq)
    ctx.writeAndFlush(kMsg)
    import akka.pattern._
    askActor.ask(AskTickerInit).mapTo[AskResponse]
  }

  override def kompactWatch(watcher: ActorRef): Unit =  {
    ctx.channel().closeFuture().addListener((_: JFuture[Void]) => {
      // Channel has been closed
      watcher ! ExecutorTerminated(this)
    })
  }

  override def kill(): Unit = ctx.close()
}

