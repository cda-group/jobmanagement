package clustermanager.yarn.utils

import org.apache.hadoop.yarn.api.records._
import org.apache.hadoop.yarn.client.api.YarnClient
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.util.Records

import scala.util.Try


class Client extends YarnConfig {
  private val conf = new YarnConfiguration()
  private val client = YarnClient.createYarnClient()

  def init(): Boolean = {
    try {
      client.init(conf)
      client.start()
      true
    } catch {
      case err: Exception =>
        println(err)
        false
    }
  }

  def launchTaskMaster(ref: String, jobId: String): Try[ApplicationId] = Try {
    val app = client.createApplication()
    val taskmasterContext = YarnTaskMaster.context(ref, jobId, conf)

    val resource = Resource.newInstance(1024, 2)
    val priority = Records.newRecord(classOf[Priority])
    priority.setPriority(1)

    val ctx = app.getApplicationSubmissionContext
    ctx.setAMContainerSpec(taskmasterContext)
    ctx.setResource(resource)
    ctx.setPriority(priority)

    val appId = ctx.getApplicationId

    client.submitApplication(ctx)

    appId
  }

}
