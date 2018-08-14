package runtime.kompact

import com.typesafe.scalalogging.LazyLogging
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import io.netty.util.ReferenceCountUtil
import runtime.kompact.messages.KompactAkkaMsg.Msg
import runtime.kompact.messages.{AskReply, ExecutorRegistration, Hello, KompactAkkaMsg}
import runtime.kompact.netty.{ProtobufDecoder, ProtobufEncoder}

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
            new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4),
            new LengthFieldPrepender(4),
            ProtobufDecoder(4),
            ProtobufEncoder(),
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
          val reply = KompactAkkaMsg(ask.askActor).withAskReply(AskReply(ask.askActor, KompactAkkaMsg().withHello(hello)))
          ctx.writeAndFlush(reply)
        case _ => println("unknown")
      }
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    logger.info(s"Client Connected to ${ctx.channel().remoteAddress()}")
    val s = ExecutorRegistration("test", "lol", "sampleActor", "hej")
    val reg = KompactAkkaMsg().withExecutorRegistration(s)
    ctx.writeAndFlush(reg)

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
