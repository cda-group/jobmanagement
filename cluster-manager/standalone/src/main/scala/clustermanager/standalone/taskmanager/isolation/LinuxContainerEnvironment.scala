package clustermanager.standalone.taskmanager.isolation

import java.io.IOException
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import clustermanager.common.{Linux, OperatingSystem}
import clustermanager.standalone.taskmanager.utils.{ContainerUtils, TaskManagerConfig}
import com.typesafe.scalalogging.LazyLogging
import runtime.protobuf.messages.Container


private[taskmanager] object LinuxContainerEnvironment extends LazyLogging {
  final case class CgroupsException(msg: String) extends Exception

  def apply(): Either[Exception, LinuxContainerEnvironment] =  {
    try {
      val availableCores = ContainerUtils.getNumberOfContainerCores
      val availableMem = ContainerUtils.getMemoryForContainers
      val env = new LinuxContainerEnvironment(availableCores, ContainerUtils.mbToBytes(availableMem))
      env.check() // Verify Cgroups Setup
      env.initRoot()
      Right(env)
    } catch {
      case err: CgroupsException =>
        logger.error(err.msg)
        Left(err)
      case ioErr: IOException =>
        logger.error(ioErr.toString)
        Left(ioErr)
    }
  }
}

/** LinuxContainerEnvironment utilises Cgroups to divide and isolate
  * resources such as CPU and Memory for each Container.
  * @param availableCores cores available to the root container Cgroup
  * @param availableMem memory available to the root container Cgroup
  */
private[taskmanager] class LinuxContainerEnvironment(availableCores: Int, availableMem: Long)
  extends Cgroups {
  import LinuxContainerEnvironment._

  /** Checks if Cgroups has been set up correctly.
    * Throws a CgroupsException if a fault is found.
    */
  private[LinuxContainerEnvironment] def check(): Unit = {
    if (OperatingSystem.get() != Linux)
      throw CgroupsException("Cgroups requires a Linux Distribution")

    if (!Files.isDirectory(Paths.get(defaultPath)))
      throw CgroupsException(s"Cgroups is not mounted at $defaultPath")


    val userDirs  = Seq(containersCpu, containersMem)

    userDirs.foreach { dir =>
      if (!Files.isDirectory(Paths.get(dir)))
        throw CgroupsException(s"Cannot find Cgroup $dir")

      if (!Files.isWritable(Paths.get(dir)))
        throw CgroupsException(s"Missing Write permissions to $dir")

      if (!Files.isReadable(Paths.get(dir)))
        throw CgroupsException(s"Missing Read permissions to $dir")
    }
  }


  /** Initializes cpu/containers and memory/containers.
    * Sets hard limits in order to ensure resources for other services
    * running on the OS.
    */
  def initRoot(): Unit = {
    // Setting Hard Limits for CPU cores:
    // Is done by giving values to cpu.cfs_quota_us and cpu.cfs_period_us
    // Example: enable full utilization of 4 CPU cores
    // cpu.cfs_quota_us = 400000, cpu.cfs_period_us = 100000

    // Period is by default 100000 on my system, check so that we don't need to actually write this down as well.
    val quota = CFS_PERIOD_VALUE * availableCores
    Files.write(Paths.get(containersCpu + "/" + CPU_CFS_QUOTA), String.valueOf(quota).getBytes)

    // Setting hard limits for Memory usage:
    // Is done by giving a value to memory.limit_in_bytes
    Files.write(Paths.get(containersMem + "/" + HARD_MEMORY_LIMIT), String.valueOf(availableMem).getBytes)

    // Just an extra verification
    assert(readToLong(Paths.get(containersCpu + "/" + CPU_CFS_QUOTA)).isDefined)
    assert(readToLong(Paths.get(containersMem + "/" + HARD_MEMORY_LIMIT)).isDefined)
  }

  /** Creates a Container Group for the specified
    * container. Throws CgroupsException on Error.
    * @param container Container
    */
  def createContainerGroup(container: Container): Boolean = {
    try {
      val cpuPath = Paths.get(containersCpu + "/" + container.jobId)
      val memPath = Paths.get(containersMem + "/" + container.jobId)

      if (Files.isDirectory(memPath))
        throw CgroupsException(s"Container already exists under $containersMem")

      if (Files.isDirectory(cpuPath))
        throw CgroupsException(s"Container already exists under $containersCpu")

      Files.createDirectory(memPath)
      Files.createDirectory(cpuPath)

      // Just an extra check
      if (!(Files.isDirectory(memPath) || Files.isDirectory(cpuPath)))
        throw CgroupsException("Something went wrong while trying to create Container Group")

      // Set Soft limit for the Container's CPU usage
      // cpu.shares = 1024 * (total CPU cores in container / Total available CPU cores in cpu/containers)
      // e.g., 1024 * (2/4) = 512 cpu shares
      val containerCores = container.tasks.foldLeft(0)(_ + _.cores)
      val shares = (1024 * (containerCores / availableCores.toDouble)).toInt
      Files.write(Paths.get(cpuPath + "/" + CPU_SHARES), String.valueOf(shares).getBytes)

      // Set Soft limit for the Container's Memory Usage
      val totalMem = container.tasks.foldLeft(0)(_ + _.memory)
      val memInBytes = ContainerUtils.mbToBytes(totalMem)
      Files.write(Paths.get(memPath + "/" + SOFT_MEMORY_LIMIT), String.valueOf(memInBytes).getBytes)

      /// Just testing
      container.tasks.foreach {task =>
        val taskCpuPath = Paths.get(cpuPath + "/" + task.name)
        val taskMemPath = Paths.get(memPath + "/" + task.name)
        Files.createDirectory(taskCpuPath)
        Files.createDirectory(taskMemPath)
        Files.write(Paths.get(taskMemPath + "/" + SOFT_MEMORY_LIMIT), String.valueOf(ContainerUtils.mbToBytes(task.memory)).getBytes())

        val taskShares = (1024 * (task.cores / containerCores.toDouble)).toInt
        Files.write(Paths.get(taskCpuPath + "/" + CPU_SHARES), String.valueOf(taskShares).getBytes())
      }

      true
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        false
    }
  }


  def createController(containerId: String): CgroupController =
    new CgroupController(containerId)

  def shutdown(): Unit = {
    // Check if there are any containers, if so try to clean them.
  }

  def getCores: Int = availableCores
  def getMemory: Long = availableMem
}


/** CgroupController is used by TaskMaster's and TaskExecutor's
  * to alter the container's cgroup
  * @param containerId ID for the Container
  */
private[taskmanager] class CgroupController(containerId: String) extends Cgroups {
  private val containerMetrics = new ContainerMetrics(containerId)
  private final val containerCpu = containersCpu + "/" + containerId
  private final val containerMem = containersMem + "/" + containerId


  /** Move process into cgroup by writing PID into the
    * groups cgroup.procs file.
    * @param pid Process ID
    * @return True on success, false otherwise
    */
  def mvProcessToCgroup(taskName: String, pid: Long): Boolean = {
    try {
      Files.write(Paths.get(containerCpu + "/" + taskName + "/" + CGROUP_PROCS) , String.valueOf(pid).getBytes,
        StandardOpenOption.APPEND)
      Files.write(Paths.get(containerMem + "/" + taskName + "/" + CGROUP_PROCS), String.valueOf(pid).getBytes,
        StandardOpenOption.APPEND)
      true
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        false
    }
  }

  /** Clean sub cgroup
    * @param name cgroup name
    * @return True on success, otherwise false
    */
  def removeCgroup(name: String): Boolean = {
    try {
      val cpu = Paths.get(containerCpu + "/" + name)
      val mem = Paths.get(containerMem + "/" + name)
      Files.deleteIfExists(cpu) && Files.deleteIfExists(mem)
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        false
    }
  }

  /** Deletes the Container cgroup under
    * cpu/containers/ and memory/containers/
    */
  def clean(): Unit =
    deleteContainerGroup(containerId)


  def metricsReporter(): ContainerMetrics = containerMetrics

}

private[taskmanager] trait Cgroups extends LazyLogging with TaskManagerConfig {

  final val defaultPath = if (cgroupsPath.isEmpty) "/sys/fs/cgroup" else cgroupsPath

  // Cgroup for containers started by the TaskManager
  final val containersCpu = defaultPath + "/" + "cpu/containers"
  final val containersMem = defaultPath + "/" + "memory/containers"

  // Identifiers
  final val HARD_MEMORY_LIMIT = "memory.limit_in_bytes"
  final val SOFT_MEMORY_LIMIT = "memory.soft_limit_in_bytes"
  final val MEMORY_USAGE = "memory.usage_in_bytes"
  final val MEMORY_FAILTCNT = "memory.failcnt"
  final val CPU_SHARES = "cpu.shares"
  final val CPU_CFS_QUOTA = "cpu.cfs_quota_us"
  final val CPU_CFS_PERIOD = "cpu.cfs_period_us"
  final val CPU_ACCT_STAT = "cpuacct.stat"
  final val CPU_ACCT_USAGE = "cpuacct.usage"
  final val CPU_ACCT_USAGE_PERCPU = "cpuacct.usage_percup"

  final val CGROUP_PROCS = "cgroup.procs"

  final val CFS_PERIOD_VALUE = 10000


  /** Used to read files with one line values
    * @param path Path to file
    * @return Option[Long]
    */
  protected def readToLong(path: Path): Option[Long] = {
    try {
      val str = Files.lines(path)
        .findFirst()
        .get()

      Some(str.toLong)
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        None
    }
  }

  /** Deletes a Container's Cgroup
    * Should be called on shutdown of TaskMaster
    * @param id Container ID
    * @return true on success, otherwise false
    */
  protected def deleteContainerGroup(id: String): Boolean = {
    val cpuPath = Paths.get(containersCpu + "/" + id)
    val memPath = Paths.get(containersMem + "/" + id)
    Files.deleteIfExists(cpuPath) && Files.deleteIfExists(memPath)
  }

  def getRootMemoryLimit: Long =
    readToLong(Paths.get(defaultPath + "/" + "memory" + "/" +  HARD_MEMORY_LIMIT))
    .getOrElse(-1)

  def getMemoryLimit(containerId: String): Long =
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + SOFT_MEMORY_LIMIT))
    .getOrElse(-1)

  def setMemoryLimit(value: Long, containerId: String): Unit = {
    Files.write(Paths.get(containersMem + "/" + containerId + "/" + SOFT_MEMORY_LIMIT),
      String.valueOf(value).getBytes())
  }
}


// Refactor
private[taskmanager] class ContainerMetrics(containerId: String) extends Cgroups {

  def getContainerMemoryUsage: Long = {
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + MEMORY_USAGE))
      .getOrElse(-1)
  }

  def getTaskMemoryUsage(name: String): Long = {
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + name + "/" + MEMORY_USAGE))
      .getOrElse(-1)
  }

  def getTaskMemoryLimit(name: String): Long = {
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + name + "/" + SOFT_MEMORY_LIMIT))
      .getOrElse(-1)
  }

  def getContainerMemFailcount: Long = {
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + MEMORY_FAILTCNT))
      .getOrElse(-1)
  }

  def getTaskMemFailcount(name: String): Long = {
    readToLong(Paths.get(containersMem + "/" + containerId + "/" + name + "/" + MEMORY_FAILTCNT))
      .getOrElse(-1)
  }

  // CPU

  def getContainerCpuAcctUsage: Long = {
    readToLong(Paths.get(containersCpu + "/" + containerId + "/" + CPU_ACCT_USAGE))
      .getOrElse(-1)
  }


}


