package clustermanager.yarn

import java.io.File

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.yarn.api.ApplicationConstants
import org.apache.hadoop.yarn.api.ApplicationConstants.Environment
import org.apache.hadoop.yarn.api.records.{ContainerLaunchContext, LocalResource, LocalResourceType, LocalResourceVisibility}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.{Apps, ConverterUtils, Records}

object YarnTaskMaster extends YarnConfig {
  import collection.JavaConverters._

  def context(appmasterRef: String, conf: YarnConfiguration): ContainerLaunchContext = {
    val ctx = Records.newRecord(classOf[ContainerLaunchContext])

    //  Commands
    ctx.setCommands(List(
      "$JAVA_HOME/bin/java" +
        " -Xmx256M " +
        s" $taskMasterClass "+
        " " + appmasterRef +
        " 1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout" +
        " 2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr"
    ).asJava)

    val jarPath = new Path(taskMasterJarPath+"/"+taskMasterJar)

    // Resources
    val resources = Map (taskMasterJar -> taskMasterLocalResource(jarPath, conf))
    ctx.setLocalResources(resources.asJava)

    // Environment
    ctx.setEnvironment(env(conf).asJava)

    ctx
  }

  private def taskMasterLocalResource(path: Path, conf: YarnConfiguration): LocalResource = {
    val stat = FileSystem.get(conf).getFileStatus(path)
    val resource = Records.newRecord(classOf[LocalResource])
    resource.setSize(stat.getLen)
    resource.setResource(ConverterUtils.getYarnUrlFromPath(path)) // Check
    resource.setTimestamp(stat.getModificationTime)
    resource.setType(LocalResourceType.FILE)
    resource.setVisibility(LocalResourceVisibility.PUBLIC)

    resource
  }


  private def env(conf: YarnConfiguration): Map[String, String] = {
    val classpath = conf.getStrings(YarnConfiguration.YARN_APPLICATION_CLASSPATH, YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH: _*)
    val envMap = new java.util.HashMap[String, String]()
    classpath.foreach(c => Apps.addToEnvironment(envMap, Environment.CLASSPATH.name(), c.trim, File.pathSeparator))

    Apps.addToEnvironment(envMap, Environment.CLASSPATH.name(), Environment.PWD.$() + File.pathSeparator + "*", File.pathSeparator)
    envMap.asScala.toMap
  }
}
