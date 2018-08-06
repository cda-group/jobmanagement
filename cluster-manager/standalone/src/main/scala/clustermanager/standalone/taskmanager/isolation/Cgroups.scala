package clustermanager.standalone.taskmanager.isolation

import java.nio.file.{Files, Path, Paths}

import clustermanager.common.{Linux, OperatingSystem}
import clustermanager.standalone.taskmanager.isolation.Cgroups.CgroupsException
import com.typesafe.scalalogging.LazyLogging




private[taskmanager] object Cgroups extends App {
  case class CgroupsException(msg: String) extends Exception

  def apply(): Cgroups = {
    val cgroups = new Cgroups()
    cgroups
  }

  try {
    val cgroups = new Cgroups()
    cgroups.check()
    println(cgroups.getRootMemoryLimit)
    cgroups.createContainerGroup("test2")
    cgroups.setMemoryLimit(10000, "test2")
  } catch {
    case err: CgroupsException =>
      println(err.msg)
  }
}

private[taskmanager] class Cgroups extends LazyLogging {

  private[this] final val defaultPath = "/sys/fs/cgroup"
  private[this] final val cpuHierarchy = defaultPath + "/" + "cpu/taskmanager/containers"
  private[this] final val memHierarchy = defaultPath + "/" + "memory/taskmanager/containers"

  private[this] final val MEMORY_LIMIT = "memory.limit_in_bytes"

  /** Checks if Cgroups has been set up correctly
    * or not. Throws a CgroupsException if a fault is found.
    */
  def check(): Unit = {
    if (OperatingSystem.get() != Linux)
      throw CgroupsException("Cgroups requires a Linux Distribution")

    if (!Files.isDirectory(Paths.get(defaultPath)))
      throw CgroupsException(s"Cannot find cgroup mount at $defaultPath")

    if (!Files.isDirectory(Paths.get(cpuHierarchy)))
      throw CgroupsException(s"Cannot find cpu hierarchy at $cpuHierarchy")

    if (!Files.isDirectory(Paths.get(memHierarchy)))
      throw CgroupsException(s"Cannot find mem hierarchy at $memHierarchy")

    if (!Files.isWritable(Paths.get(cpuHierarchy)))
      throw CgroupsException(s"Missing Write permissions to $cpuHierarchy")

    if (!Files.isReadable(Paths.get(cpuHierarchy)))
      throw CgroupsException(s"Missing Read permissions to $cpuHierarchy")

    if (!Files.isWritable(Paths.get(memHierarchy)))
      throw CgroupsException(s"Missing Write permissions to $memHierarchy")

    if (!Files.isReadable(Paths.get(memHierarchy)))
      throw CgroupsException(s"Missing Read permissions to $memHierarchy")
  }

  /** Creates a Container Group for the specified
    * container. Throws CgroupsException on Error.
    * @param id Container ID
    */
  def createContainerGroup(id: String): Unit = {
    val cpuPath = Paths.get(cpuHierarchy + "/" + id)
    val memPath = Paths.get(memHierarchy + "/" + id)

    if (Files.isDirectory(memPath))
      throw CgroupsException(s"Container already exists under $memHierarchy")

    if (Files.isDirectory(cpuPath))
      throw CgroupsException(s"Container already exists under $cpuHierarchy")

    Files.createDirectory(memPath)
    Files.createDirectory(cpuPath)

    // Just an extra check
    if (!(Files.isDirectory(memPath) || Files.isDirectory(cpuPath)))
      throw CgroupsException("Something went wrong while trying to create Container Group")
  }

  def getRootMemoryLimit: Long =
    readToLong(Paths.get(memHierarchy + "/" + MEMORY_LIMIT))

  def getMemoryLimit(containerId: String): Long =
    readToLong(Paths.get(memHierarchy + "/" + containerId + "/" + MEMORY_LIMIT))

  def setMemoryLimit(value: Long, containerId: String): Unit = {
    Files.write(Paths.get(memHierarchy + "/" + containerId + "/" + MEMORY_LIMIT),
      String.valueOf(value).getBytes())
  }

  /** Move process into cgroup by writing PID into the
    * groups tasks file.
    * @param pid Process which we are moving
    * @param containerId Id of the Container
    * @return True on success, false otherwise
    */
  def mvProcessToCgroup(pid: Long, containerId: String): Boolean = {
    // write pid into containerId/tasks file
    true
  }



  /** Used to read files with one line values
    * @param path Path to file
    * @return Long, returns -1 on error
    */
  private def readToLong(path: Path): Long = {
    try {
      val str = Files.lines(path)
        .findFirst()
        .get()

      str.toLong
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        -1
    }
  }
}
