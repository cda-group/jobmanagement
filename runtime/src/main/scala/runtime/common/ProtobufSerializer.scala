package runtime.common

import java.nio.charset.StandardCharsets

import akka.serialization.{Serialization, SerializerWithStringManifest}
import akka.actor.{ActorRef, ExtendedActorSystem}
import runtime.common.models.TaskManagerInit


class ProtobufSerializer(system: ExtendedActorSystem) extends SerializerWithStringManifest {
  override def identifier: Int = 1313131

  override def manifest(o: AnyRef): String = o.getClass.getName

  private final val TaskManagerInitManifest = classOf[TaskManagerInit].getName
  private final val ActorRefManifest = classOf[ActorRef].getName


  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case tmi: TaskManagerInit => tmi.toByteArray
    case aref: ActorRef => Serialization.serializedActorPath(aref).getBytes
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case TaskManagerInitManifest => TaskManagerInit.parseFrom(bytes)
    case ActorRefManifest =>
      val str = new String(bytes, StandardCharsets.UTF_8)
      system.provider.resolveActorRef(str)
  }


}

