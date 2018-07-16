package runtime.protobuf

import akka.actor.{ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}

private[runtime] object ExternalAddress extends ExtensionId[ExternalAddressExt] with ExtensionIdProvider {
  override def lookup() = ExternalAddress

  override def createExtension(system: ExtendedActorSystem): ExternalAddressExt =
    new ExternalAddressExt(system)

  override def get(system: ActorSystem): ExternalAddressExt = super.get(system)
}
