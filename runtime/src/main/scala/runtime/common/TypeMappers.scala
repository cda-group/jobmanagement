package runtime.common

import com.google.protobuf.ByteString
import runtime.common.models.{ArcProfile, SlotRequestResp}
import runtime.resourcemanager.actors.ResourceManager.SlotRequest

import scalapb.TypeMapper

object TypeMappers {

  case class Aprofile(p: ArcProfile) {
    def matches(other: ArcProfile): Boolean =
      this.p.cpuCores >= other.cpuCores && this.p.memoryInMb >= other.memoryInMb
    def toProto: ArcProfile = this.p
  }

  object Aprofile {
    implicit val typeMapper: TypeMapper[ArcProfile, Aprofile] = TypeMapper(apply)(_.toProto)
  }

  case class SlotResp(r: SlotRequestResp)

  object SlotResp {
    private def unapplySlotResp(arg: SlotResp): ByteString =
      ByteString.copyFrom(SlotResp.toString.getBytes)

    //implicit val typeMapper: TypeMapper[SlotRequestResp, ByteString] =
  }
}

