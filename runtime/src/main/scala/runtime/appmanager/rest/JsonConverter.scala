package runtime.appmanager.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import runtime.appmanager.actors.AppManager.TaskReport
import runtime.common.messages.{WeldJob, WeldTask}
import spray.json.DefaultJsonProtocol

/**
  * JSON Marshaller/Unmarshaller
  * Should probably look into changing this to Circe later on..
  */
trait JsonConverter extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val weldTaskFormat = jsonFormat3(WeldTask.apply)
  implicit val weldJobFormat = jsonFormat1(WeldJob.apply)
  implicit val taskReport = jsonFormat1(TaskReport.apply)
}
