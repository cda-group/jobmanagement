package clustermanager.yarn.utils

import org.apache.hadoop.fs.Path
import org.apache.hadoop.yarn.api.ApplicationConstants
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.Records

object YarnTaskExecutor extends YarnConfig {
  import collection.JavaConverters._

  /** Creates a ContainerLaunchContext for our TaskExecutor
    *
    * @param taskMasterRef ActorRef in String
    * @param appMasterRef ActorRef in String
    * @param jobId job ID of Arc Job
    * @param taskId Which id the ArcTask has
    * @param binPath path to the binary
    * @return ContainerLaunchContext
    */
  def context(taskMasterRef: String,
              appMasterRef: String,
              stateMasterRef: String,
              jobId:String,
              taskId: Int,
              binPath: String): ContainerLaunchContext = {

    val conf = new YarnConfiguration()
    val ctx = Records.newRecord(classOf[ContainerLaunchContext])

    //  Commands
    ctx.setCommands(List(
      "$JAVA_HOME/bin/java" +
        " -Xmx256M " +
        s" $taskExecutorClass"+
        " " + binPath +
        " " + jobId +
        " " + taskId.toString +
        " " + taskMasterRef +
        " " + appMasterRef +
        " " + stateMasterRef +
        " 1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout" +
        " 2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr"
    ).asJava)

    val jarPath = new Path(taskExecutorJarPath +"/"+taskExecutorJar)

    // Resources
    val resources = Map (taskExecutorJar -> YarnUtils.setLocalResource(jarPath, conf))
    ctx.setLocalResources(resources.asJava)

    // Environment
    ctx.setEnvironment(YarnUtils.setEnv(conf).asJava)

    ctx
  }

}
