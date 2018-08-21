package clustermanager.common.executor

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, Paths}
import java.util.Comparator
import java.util.stream.Collectors

import clustermanager.common.{Linux, Mac, OperatingSystem, Windows}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/** ExecutionEnvironment is used by TaskMaster's/TaskExecutor's
  * to create an "isolated" environment for an ArcJob
  */
private[clustermanager] class ExecutionEnvironment(jobId: String) extends LazyLogging {

  // For now. To be discussed
  private final val LINUX_DIR = System.getProperty("user.home") + "/arc"
  private final val MAC_OS_DIR = System.getProperty("user.home") + "/arc"

  private final val LINUX_JOB_PATH = LINUX_DIR + "/" + jobId
  private final val MAC_OS_JOB_PATH = MAC_OS_DIR + "/" + jobId

  /**
    * Create a directory where the job will execute
    * Path will depend on OS
    */
  def create(): Try[Boolean] = Try {
    OperatingSystem.get() match {
      case Linux =>
        createLinuxJobDir().isSuccess
      case Mac =>
        // createMacJobDIr().isSuccess
        false
      case Windows => // We shouldn't really get here
        false
      case _ =>
        false
    }
  }


  /**
    * Creates a directory with the job's id.
    * If the environment does not exist, it will create it.
    */
  private def createLinuxJobDir(): Try[Boolean] = Try {
    if (Files.exists(Paths.get(LINUX_DIR))) {
      Files.createDirectories(Paths.get(LINUX_JOB_PATH))
      true
    } else {
      createLinuxEnv() match {
        case Success(_) =>
          Files.createDirectories(Paths.get(LINUX_JOB_PATH))
          true
        case Failure(e) =>
          logger.error("Could not create linux env")
          logger.error(e.toString)
          false
      }
    }
  }

  /**
    * Take the bytes and saves it to a file in the execution environment.
    * Used by Standalone
    */
  def writeBinaryToFile(id: String, file: Array[Byte]): Boolean  = {
    OperatingSystem.get() match {
      case Linux =>
        Files.write(Paths.get(LINUX_JOB_PATH+"/"+id), file)
        setAsExecutable(LINUX_JOB_PATH+"/"+id) match {
          case Success(_) =>
            true
          case Failure(e) =>
            logger.error(e.toString)
            false
        }
      case Mac => false
      case _ => false
    }
  }

  /** Make a binary executable.
    * Compatiable with UNIX/Linux
    * @param path Path to the binary
    * @return Scala Try
    */
  def setAsExecutable(path: String): Try[Unit] = Try {
    import scala.collection.JavaConverters._

    val perms: mutable.HashSet[PosixFilePermission] = mutable.HashSet()
    perms += PosixFilePermission.OWNER_EXECUTE
    perms += PosixFilePermission.OWNER_READ
    perms += PosixFilePermission.OWNER_WRITE
    Files.setPosixFilePermissions(Paths.get(path), perms.asJava)
  }

  /**
    * Delete resources tied to the ExecutionEnvironment
    */
  def clean(): Unit = {
    import scala.collection.JavaConverters._

    OperatingSystem.get() match {
      case Linux =>
        val toDelete = Files.walk(Paths.get(LINUX_JOB_PATH))
          .sorted(Comparator.reverseOrder())
          .collect(Collectors.toList())
          .asScala

        toDelete.foreach(Files.deleteIfExists(_))
      case Mac =>
      //TODO
      case _ =>
    }
  }



  private def createLinuxEnv(): Try[Unit] = Try {
    Files.createDirectories(Paths.get(LINUX_DIR),
      PosixFilePermissions.asFileAttribute(
        PosixFilePermissions.fromString("rwxr-x---") //TODO: look into fitting permissions
      ))
  }

  def getJobPath: String =
    OperatingSystem.get() match {
      case Linux => LINUX_JOB_PATH
      case Mac => MAC_OS_JOB_PATH
      case _ => ""
    }
}
