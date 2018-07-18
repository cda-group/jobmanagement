package runtime.taskmaster.common

final case class TaskMasterConf(heartbeat: Long, hostname: String)
final case class TaskExecutorConf(monitorTicker: Long)

