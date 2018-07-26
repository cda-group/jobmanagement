package clustermanager.yarn.client

import com.typesafe.config.ConfigFactory

trait YarnConfig {
  val config = ConfigFactory.load("yarn")

  val jobsDir = config.getString("yarn.taskmaster-jobs-dir")

  val taskMasterMemory = config.getInt("yarn.taskmaster-memory")
  val taskMasterCores = config.getInt("yarn.taskmaster-cores")
  val taskMasterPriority = config.getInt("yarn.taskmaster-priority")

  val taskMasterJarPath = config.getString("yarn.taskmaster-jar-path")
  val taskMasterJar = config.getString("yarn.taskmaster-jar")
  val taskMasterClass = config.getString("yarn.taskmaster-main-class")

  val taskExecutorJarPath = config.getString("yarn.taskexecutor-jar-path")
  val taskExecutorJar = config.getString("yarn.taskexecutor-jar")
  val taskExecutorClass = config.getString("yarn.taskexecutor-main-class")


  require(config.isResolved, "YarnConfig has not been resolved")
  require(jobsDir.nonEmpty, "HDFS jobs directory is not defined")

  require(taskMasterJarPath.nonEmpty, "TaskMaster's Jar Path is not defined")
  require(taskMasterJar.nonEmpty, "TaskMaster's Jar is not defined")
  require(taskMasterClass.nonEmpty, "TaskMaster class is not defined")


  require(taskExecutorClass.nonEmpty, "TaskExecutor class is not defined")
  require(taskExecutorJar.nonEmpty, "TaskExecutor Jar is not defined")
  require(taskExecutorJarPath.nonEmpty, "TaskExecutor's Jar Path is not defined")


  require(taskMasterMemory > 0, "TaskMaster's memory is not defined")
  require(taskMasterCores > 0, "TaskMaster's cores is not defined")
  require(taskMasterPriority > 0, "TaskMaster's priority is not defined")
}
