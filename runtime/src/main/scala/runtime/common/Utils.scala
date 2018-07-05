package runtime.common

import runtime.common.models.ArcProfile


object Utils {

  // For development
  def testResourceProfile(): ArcProfile =
    ArcProfile(2.0, 2000) // 2.0 cpu core & 2000MB mem

  def slotProfile(): ArcProfile =
    ArcProfile(1.0, 1000) // 1.0 cpu core & 1000MB mem
}
