package runtime.kompact

import java.nio.ByteOrder

import com.typesafe.scalalogging.LazyLogging
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import io.netty.util.ReferenceCountUtil
import runtime.kompact.messages.KompactAkkaMsg.Msg
import runtime.kompact.messages._
import runtime.kompact.netty.{KompactDecoder, KompactEncoder}

import scala.io.StdIn

object ClientTest extends App with LazyLogging {
  val client = new Client()
  logger.info("Service is now started")

  val future = client.run()

  /*
  future.addListener(new ChannelFutureListener {
    override def operationComplete(future: ChannelFuture): Unit = {
      if (future.isSuccess) {
        println("connected!")

        var line = ""
        while ({line = StdIn.readLine(); line != null}) {
          val msg = KompactAkkaMsg().withHello(Hello(line))
          if (future.channel().isWritable) {
            future.channel().writeAndFlush(msg)
          }
        }

      } else {
        println("failed")
      }
    }
  })
  future.channel().closeFuture().sync()
  */
}

class ClientTest {

}

class Client() extends LazyLogging {
  private val bGroup = new NioEventLoopGroup()

  def run(): Unit = {
    val host = "localhost"
    val port = 2020

    try {
      val bootstrap = new Bootstrap()
      bootstrap.group(bGroup)
      bootstrap.channel(classOf[NioSocketChannel])
      bootstrap.option(ChannelOption.SO_KEEPALIVE, true: java.lang.Boolean)

      bootstrap.handler(new ChannelInitializer[SocketChannel](){
        @throws[Exception]
        override def initChannel(channel: SocketChannel): Unit = {
          channel.pipeline().addLast(
            new LengthFieldBasedFrameDecoder(ByteOrder.BIG_ENDIAN, Integer.MAX_VALUE, 0, 4, -4, 0, false),
            new LengthFieldPrepender(ByteOrder.BIG_ENDIAN, 4, 0, true),
            new KompactDecoder(),
            new KompactEncoder(),
            ClientHandler()
          )
        }
      })
      logger.info("Connecting to server")

      val future = bootstrap.connect(host, port).sync()
      future.channel().closeFuture().sync()
    } finally {
      bGroup.shutdownGracefully()
    }
  }

  def close(): Unit = {
    bGroup.shutdownGracefully()
  }
}

final case class ClientHandler() extends ChannelInboundHandlerAdapter with LazyLogging {
  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    try {
      val e: KompactAkkaMsg = msg.asInstanceOf[KompactAkkaMsg]
      e.msg match {
        case Msg.Hello(v) =>
          println(v)
        case Msg.Ask(ask) =>
          val hello = Hello("gotyaback")
          val reply = KompactAkkaMsg().withAskReply(AskReply(ask.askActor, KompactAkkaMsg().withHello(hello)))
          ctx.writeAndFlush(reply)
        case _ => println("unknown")
      }
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    logger.info(s"Client Connected to ${ctx.channel().remoteAddress()}")
    val src = KompactAkkaPath("executor", "127.0.0.1", 2020)
    val dst = KompactAkkaPath("executor", "127.0.0.1", 2020)
    val s = ExecutorRegistration("test", src, dst)
    val reg = KompactAkkaMsg(src, dst).withExecutorRegistration(s)
    ctx.writeAndFlush(reg)

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
