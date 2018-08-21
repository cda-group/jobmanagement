package clustermanager.yarn.client

import org.apache.hadoop.fs.Path
import org.apache.hadoop.yarn.api.ApplicationConstants
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.Records

object YarnTaskMaster extends YarnConfig {
  import collection.JavaConverters._

  /** Creates a ContainerLaunchContext for a YARN
    * ApplicationMaster, in our case a TaskMaster.
    * @param appmasterRef ActorRef in String format
    * @param stateMasterRef ActorRef in String format
    * @param jobId Job ID of the Arc JOb
    * @param conf
    * @return
    */
  def context(appmasterRef: String,
              stateMasterRef: String,
              jobId: String,
              conf: YarnConfiguration): ContainerLaunchContext = {
    val ctx = Records.newRecord(classOf[ContainerLaunchContext])

    //  Commands
    ctx.setCommands(List(
      "$JAVA_HOME/bin/java" +
        " -Xmx256M " +
        s" $taskMasterClass "+
        " " + appmasterRef +
        " " + stateMasterRef +
        " " + jobId +
        " 1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout" +
        " 2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr"
    ).asJava)

    val jarPath = new Path(taskMasterJarPath+"/"+taskMasterJar)

    // Resources
    val resources = Map (taskMasterJar -> YarnUtils.setLocalResource(jarPath, conf))
    ctx.setLocalResources(resources.asJava)

    // Environment
    ctx.setEnvironment(YarnUtils.setEnv(conf).asJava)

    ctx
  }
}
