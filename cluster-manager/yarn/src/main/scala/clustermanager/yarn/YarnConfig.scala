package clustermanager.yarn

import com.typesafe.config.ConfigFactory

trait YarnConfig {
  val config = ConfigFactory.load("yarn")
  val taskMasterJarPath = config.getString("yarn.taskmaster-jar-path")
  val taskMasterJar = config.getString("yarn.taskmaster-jar")
  val taskMasterClass = config.getString("yarn.taskmaster-main-class")
  val jobsDir = config.getString("yarn.taskmaster-jobs-dir")

  require(config.isResolved)
  require(taskMasterJarPath.nonEmpty)
  require(taskMasterJar.nonEmpty)
  require(taskMasterClass.nonEmpty)
  require(jobsDir.nonEmpty)
}
