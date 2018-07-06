package runtime.common.messages


trait ProfileMatcher {
  this: ArcProfile =>
  def matches(other: ArcProfile): Boolean =
    this.cpuCores >= other.cpuCores && this.memoryInMb >= other.memoryInMb
}
