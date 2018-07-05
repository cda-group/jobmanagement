package runtime.common

import com.google.protobuf.ByteString
import runtime.common.models.{ArcProfile, SlotRequestResp}

import scalapb.TypeMapper

object TypeMappers {


  private def applySlotRequestResp(bytes: ByteString): SlotRequestResp = {
    if (bytes.size > 0) {
      null
    } else {
      null
    }
  }

  case class Aprofile(p: ArcProfile) {
    def matches(other: ArcProfile): Boolean =
      this.p.cpuCores >= other.cpuCores && this.p.memoryInMb >= other.memoryInMb
    def toProto: ArcProfile = this.p
  }

  object Aprofile {
    implicit val typeMapper: TypeMapper[ArcProfile, Aprofile] = TypeMapper(apply)(_.toProto)
  }
}

