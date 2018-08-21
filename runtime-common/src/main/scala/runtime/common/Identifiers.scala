package runtime.common

object Identifiers {
  final val RESOURCE_MANAGER = "resourcemanager"
  final val LISTENER = "listener"
  final val SLOT_MANAGER = "slotmanager"
  final val TASK_MANAGER = "taskmanager"
  final val USER = "user"
  final val SCHEDULER = "scheduler"
  final val APP_MASTER= "appmaster"
  final val APP_MANAGER = "appmanager"
  final val TASK_MASTER = "taskmaster"
  final val TASK_EXECUTOR = "taskexecutor"
  final val STATE_MANAGER = "statemanager"
  final val STATE_MASTER = "statemaster"

  final val CLUSTER = "ArcRuntime"

  // ArcTask states
  final val ARC_TASK_RUNNING = "running"
  final val ARC_TASK_PENDING = "pending"
  final val ARC_TASK_FINISHED = "finished"
  final val ARC_TASK_KILLED = "killed"
  final val ARC_TASK_TRANSFER_ERROR = "transfer error"

  // ArcJob states
  final val ARC_JOB_DEPLOYING = "deploying"
  final val ARC_JOB_INITIALIZING = "initializing"
  final val ARC_JOB_RUNNING = "running"
  final val ARC_JOB_KILLED = "killed"
  final val ARC_JOB_FAILED = "failed"
  final val ARC_JOB_SUCCEEDED = "succeeded"
}
