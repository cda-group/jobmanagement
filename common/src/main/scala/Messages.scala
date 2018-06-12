package common


// to start of with
case class JobRequest(id: String)
case class JobAck(id: String)


case class WorkerState(cpu: Long, mem: Long)
case object WorkerRegistration
case object WorkerInit

