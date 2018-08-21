package runtime.kompact.netty

import java.util

import com.typesafe.scalalogging.LazyLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import runtime.kompact.messages.KompactAkkaMsg



private[kompact] final case class ProtobufDecoder(lengthSize: Int)
  extends MessageToMessageDecoder[ByteBuf] with LazyLogging {
  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes() > lengthSize) {
      var offset = 0
      var array: Array[Byte] = null
      val length = in.readableBytes()

      if (in.hasArray) {
        array = in.array()
        offset = in.arrayOffset() + in.readerIndex()
      } else {
        array = new Array[Byte](length)
        in.getBytes(in.readerIndex(), array, offset , length)
      }

      out.add(KompactAkkaMsg.parseFrom(array))
    }
  }
}
