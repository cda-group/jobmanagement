package runtime.protobuf

import akka.actor.{Address, ExtendedActorSystem, Extension}

class ExternalAddressExt(system: ExtendedActorSystem) extends Extension {
  def addressForAkka: Address = system.provider.getDefaultAddress
}
