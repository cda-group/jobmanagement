package clustermanager.yarn.client

import com.typesafe.scalalogging.LazyLogging
import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.YarnClient
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.exceptions.YarnException
import org.apache.hadoop.yarn.util.Records

import scala.util.Try


class Client extends YarnConfig with LazyLogging {
  private val conf = new YarnConfiguration()
  private val client = YarnClient.createYarnClient()

  def init(): Boolean = {
    try {
      client.init(conf)
      client.start()
      true
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        false
    }
  }

  /** TaskMaster --> YARN ApplicationMaster
    *
    * @param appMaster ActorRef in String format
    * @param stateMaster ActorRef in String format
    * @param jobId ID of the Job
    * @return ApplicationID
    */
  def launchTaskMaster(appMaster: String, stateMaster: String, jobId: String): Try[ApplicationId] = Try {
    val app = client.createApplication()
    val taskmasterContext = YarnTaskMaster.context(appMaster, stateMaster, jobId, conf)

    val resource = Resource.newInstance(taskMasterMemory, taskMasterCores)
    val priority = Records.newRecord(classOf[Priority])
    priority.setPriority(taskMasterPriority)

    val ctx = app.getApplicationSubmissionContext
    ctx.setAMContainerSpec(taskmasterContext)
    ctx.setResource(resource)
    ctx.setPriority(priority)

    val appId = ctx.getApplicationId

    client.submitApplication(ctx)

    appId
  }


  /** Fetches the current state of the job on YARN
    * @param id ID of the YARN job
    * @return Current State
    */
  def getAppStatus(id: ApplicationId): Option[YarnApplicationState] = {
    try {
      Some(client.getApplicationReport(id).getYarnApplicationState)
    } catch {
      case err: Exception =>
        logger.error(err.toString)
        None
    }
  }

}
