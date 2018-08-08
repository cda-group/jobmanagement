package clustermanager.standalone.taskmanager.utils

import clustermanager.common.Hardware

object ContainerUtils extends TaskManagerConfig {

  /** Fetches number of cores that should be used for ContainerSlice's
    * @return Cores in Integer
    */
  def getNumberOfContainerCores: Int = {
    val cores = Hardware.getNumberCPUCores // Should be fixed to read from proc/cpuinfo
    (cores * (resourceLimit / 100.toDouble)).toInt
  }

  /** Fetches Memory in MB that can be allocated for ContainerSlice's
    * @return Long MB
    */
  def getMemoryForContainers: Long = {
    val memory = Hardware.getSizeOfPhysicalMemory
    val inMb = memory / (1024L*1024L)
    val procentage = resourceLimit / 100.toDouble
    val newMb = (inMb * procentage).toInt
    (Math.floor(newMb/ 1000.0) * 1000).toLong
  }

  def mbToBytes(mb: Long): Long =
    (mb * 1024) * 1024

}
