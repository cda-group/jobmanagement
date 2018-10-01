package runtime.protobuf.messages

private[runtime] trait ProfileMatcher {
  this: ResourceProfile =>
  def matches(other: ResourceProfile): Boolean =
    this.cpuCores >= other.cpuCores && this.memoryInMb >= other.memoryInMb
}
