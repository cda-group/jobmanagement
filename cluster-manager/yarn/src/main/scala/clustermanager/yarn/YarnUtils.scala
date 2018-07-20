package clustermanager.yarn

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.yarn.conf.YarnConfiguration

object YarnUtils extends YarnConfig with LazyLogging {


  /** Distributes a binary to HDFS
    *
    * @param jobId Arc Job ID
    * @param localFilePath path to local binary
    * @return tuple of (success/fail and path to binary)
    */
  def moveToHDFS(jobId: String, localFilePath: String): Option[Path] = {
    val conf = new YarnConfiguration()

    val destFs = FileSystem.get(conf)

    createDirectories(destFs, jobId)

    // TODO add unique identifier for each binary in a job
    val destPath = destFs.makeQualified(new Path(jobsDir+"/"+jobId+"/"+"my_binary"))

    if (destFs.exists(destPath)) {
      logger.error("copyToHDFS: Destination path already exists: " + destPath.toString)
      None
    } else {
      logger.info(s"Transfering $localFilePath to $destPath")
      try {
        // False means to not remove the file from the local system
        destFs.copyFromLocalFile(false, new Path(localFilePath),  destPath)
        Some(destPath)
      } catch {
        case err: Exception =>
          logger.error(err.toString)
          None
      }
    }
  }

  private def createDirectories(destFs:FileSystem, jobId: String): Unit = {
    val jobRootPath = destFs.makeQualified(new Path(jobsDir))
    if (!destFs.exists(jobRootPath))
      destFs.mkdirs(jobRootPath)

    val jobsDirPath = destFs.makeQualified(new Path(jobsDir, jobId))
    if (!destFs.exists(jobsDirPath))
      destFs.mkdirs(jobsDirPath)
  }

}
