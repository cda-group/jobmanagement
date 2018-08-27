package runtime.kompact.netty

import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter, SimpleChannelInboundHandler}
import io.netty.util.ReferenceCountUtil
import runtime.kompact.ProxyActor.AskRelay
import runtime.kompact.{ExecutorUp, KompactRef}
import runtime.kompact.messages.KompactAkkaMsg.Msg
import runtime.kompact.messages.KompactAkkaMsg

import scala.concurrent.{ExecutionContext, Future}


/** ProxyServerHandler is responsible for handling
  * each Executor Connnection.
  * @param proxy ActorRef
  */
private[kompact] class ProxyServerHandler(proxy: ActorRef, group: NioEventLoopGroup)
  extends SimpleChannelInboundHandler[KompactAkkaMsg] with LazyLogging {
  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(3.seconds)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(group)

  private var akkaActor: Option[ActorRef] = None

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    logger.info(s"New Executor Connected ${ctx.channel().remoteAddress()}")
  }

  override def channelRead0(ctx: ChannelHandlerContext, payload: KompactAkkaMsg): Unit = {
    try {
      payload.msg match {
        case Msg.Hello(v) => akkaActor match {
          case Some(ref) => ref ! v
          case None => logger.error("Ref not set yet")
        }
        case Msg.AskReply(reply) =>
          proxy ! AskRelay(reply)
        case Msg.ExecutorRegistration(reg) =>
          val kRef = KompactRef(reg.jobId, reg.src, reg.dst, ctx)
          proxy ? ExecutorUp(kRef) map {
            case ref: ActorRef =>
              akkaActor = Some(ref)
              logger.info("Akka ref set")
            case _ =>
              logger.error("Could not locate ActorRef for " + kRef.dstPath.path)
          }
        case _ => println("unknown")
      }
    } finally {
      ReferenceCountUtil.release(payload)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {

  }
  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    ctx.close()
  }
  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    ctx.close()
  }

  private def lookupRef(kRef: KompactRef): Future[ActorRef] = {
    proxy ? ExecutorUp(kRef) flatMap {
      case ref: ActorRef => Future.successful(ref)
      case _ => Future.failed(new Exception("Failed fetching ref"))
    }
  }

}
