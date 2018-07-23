package clustermanager.yarn.utils

import java.io.{BufferedOutputStream, File, FileOutputStream, InputStream}

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.io.IOUtils
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment
import org.apache.hadoop.yarn.api.records.{LocalResource, LocalResourceType, LocalResourceVisibility}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.{Apps, ConverterUtils, Records}

object YarnUtils extends YarnConfig with LazyLogging {


  /** Distributes a binary to HDFS
    *
    * @param jobId Arc Job ID
    * @param localFilePath path to local binary
    * @return Option of the Destination path
    */
  def moveToHDFS(jobId: String, taskName: String, localFilePath: String): Option[Path] = {
    val conf = new YarnConfiguration()

    val destFs = FileSystem.get(conf)

    createDirectories(destFs, jobId)

    val destPath = destFs.makeQualified(new Path(jobsDir+"/"+jobId+"/"+ taskName))

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


  /** Writes binary to local filesystem from @HDFS
    *
    * @param src HDFS path to the binary
    * @param local local path on the filesystem (in the job's executionenvironment)
    * @return true on success, otherwise false
    */
  def moveToLocal(src: String, local: String): Boolean = {
    try {
      val conf = new YarnConfiguration()
      val fs = FileSystem.get(conf)
      val srcPath = new Path(src)
      val inputStream = fs.open(srcPath)
      val outputStream = new BufferedOutputStream(new FileOutputStream(local))
      logger.info(s"Moving $srcPath to $local")
      IOUtils.copyBytes(inputStream, outputStream, conf)
      true
    } catch {
      case err: Exception =>
        logger.error("Failed to move binary to local filesystem with error: " + err.toString)
        false
    }
  }


  def setLocalResource(path: Path, conf: YarnConfiguration): LocalResource = {
    val stat = FileSystem.get(conf).getFileStatus(path)
    val resource = Records.newRecord(classOf[LocalResource])
    resource.setSize(stat.getLen)
    resource.setResource(ConverterUtils.getYarnUrlFromPath(path)) // Check
    resource.setTimestamp(stat.getModificationTime)
    resource.setType(LocalResourceType.FILE)
    resource.setVisibility(LocalResourceVisibility.PUBLIC)

    resource
  }

  def setEnv(conf: YarnConfiguration): Map[String, String] = {
    import collection.JavaConverters._
    val classpath = conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH, YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH: _*)
    val envMap = new java.util.HashMap[String, String]()
    classpath.foreach(c => Apps.addToEnvironment(envMap, Environment.CLASSPATH.name(), c.trim, File.pathSeparator))

    Apps.addToEnvironment(envMap, Environment.CLASSPATH.name(), Environment.PWD.$() + File.pathSeparator + "*", File.pathSeparator)
    envMap.asScala.toMap
  }

}
