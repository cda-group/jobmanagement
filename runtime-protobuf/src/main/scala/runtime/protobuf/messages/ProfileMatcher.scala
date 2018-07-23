package runtime.protobuf.messages

private[runtime] trait ProfileMatcher {
  this: ArcProfile =>
  def matches(other: ArcProfile): Boolean =
    this.cpuCores >= other.cpuCores && this.memoryInMb >= other.memoryInMb
}
