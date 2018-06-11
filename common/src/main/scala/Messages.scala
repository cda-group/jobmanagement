package common

// Actor messages

case class Job(id: String)
case class JobAck(id: String)
case object Test
case object HeartBeat


case class WorkerState(cpu: Int, mem: Int)
case object WorkerRegistration

//
case object HeartbeatInit
