package runtime.kompact.netty

import io.netty.channel.embedded.EmbeddedChannel
import org.scalatest.FlatSpec
import runtime.kompact.messages.{Hello, KompactAkkaMsg}


class ProtobufSpec extends FlatSpec {

  "Protobuf encoder and decoder" should "actually work" in {
    val channel = new EmbeddedChannel(ProtobufEncoder(), ProtobufDecoder(4))
    val protoMsg = KompactAkkaMsg("path").withHello(Hello("hey"))
    channel.writeOutbound(protoMsg)
    channel.writeInbound(channel.readOutbound())
    val obj: KompactAkkaMsg = channel.readInbound()
    assert(obj.msg.isHello)
  }
}
