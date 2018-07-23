package clustermanager.yarn.utils

import com.typesafe.config.ConfigFactory

trait YarnConfig {
  val config = ConfigFactory.load("yarn")

  val jobsDir = config.getString("yarn.taskmaster-jobs-dir")

  val taskMasterJarPath = config.getString("yarn.taskmaster-jar-path")
  val taskMasterJar = config.getString("yarn.taskmaster-jar")
  val taskMasterClass = config.getString("yarn.taskmaster-main-class")

  val taskExecutorJarPath = config.getString("yarn.taskexecutor-jar-path")
  val taskExecutorJar = config.getString("yarn.taskexecutor-jar")
  val taskExecutorClass = config.getString("yarn.taskexecutor-main-class")


  require(config.isResolved)
  require(taskMasterJarPath.nonEmpty)
  require(taskMasterJar.nonEmpty)
  require(taskMasterClass.nonEmpty)
  require(jobsDir.nonEmpty)
  require(taskExecutorClass.nonEmpty)
  require(taskExecutorJar.nonEmpty)
  require(taskExecutorJarPath.nonEmpty)
}
