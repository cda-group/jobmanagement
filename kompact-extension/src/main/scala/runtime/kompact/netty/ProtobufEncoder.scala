package runtime.kompact.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import runtime.kompact.messages.KompactAkkaMsg


private[kompact] final case class ProtobufEncoder() extends MessageToByteEncoder[KompactAkkaMsg] {
  override def encode(ctx: ChannelHandlerContext, msg: KompactAkkaMsg, out: ByteBuf): Unit =
    out.writeBytes(msg.toByteArray)
}
