package runtime.protobuf

import akka.actor.{ActorRef, ActorSystem, Address, ExtendedActorSystem}
import java.net.{InetSocketAddress => InetSocketAddressJava}
import messages.{ActorRefProto, AddressProto, InetProto}


object ProtoConversions {

  object ActorRef {
    implicit def toRef(p: ActorRefProto)(implicit system: ActorSystem): ActorRef =
      system.asInstanceOf[ExtendedActorSystem].provider.resolveActorRef(p.path)
    implicit def toProto(ref: ActorRef)(implicit system: ActorSystem) : ActorRefProto =
      ActorRefProto(ref.path.toSerializationFormatWithAddress(ExternalAddress(system).addressForAkka))
  }

  object Address {
    implicit def toProto(addr: Address): AddressProto =
      AddressProto(addr.system, addr.host.getOrElse(""), addr.port.get, addr.protocol)
    implicit def toAddress(ap: AddressProto): Address =
      new Address(ap.protocol, ap.system, ap.hostname, ap.port)
  }

  object InetAddr {
    implicit def inetToProto(inet: InetSocketAddressJava): InetProto =
      InetProto(inet.getHostName, inet.getPort)

    implicit def protoToInet(inet: InetProto): InetSocketAddressJava =
      new InetSocketAddressJava(inet.ip, inet.port)
  }
}
