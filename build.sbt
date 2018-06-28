import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "jobmanagement." + "root"

lazy val generalSettings = Seq(
  // can be changed
  organization := "se.sics.cda",
  scalaVersion := "2.12.6"
)

lazy val runtimeSettings = generalSettings ++ Seq(
  fork in run := true,  // https://github.com/sbt/sbt/issues/3736#issuecomment-349993007
  cancelable in Global := true,
  version := "0.1"
)

lazy val taskmanager= (project in file("runtime"))
  .settings(runtimeSettings: _*)
  .settings(
    target := file("runtime/tmtarget"),
    mainClass in assembly := Some("taskmanager.TaskManagerSystem"),
    assemblyJarName in assembly := "taskmanager.jar",
    libraryDependencies ++= Dependencies.runtimeDependencies
  )

lazy val resourcemanager = (project in file("runtime"))
  .settings(runtimeSettings: _*)
  .settings(
    target := file("runtime/rmtarget"),
    mainClass in (Compile, run) := Some("resourcemanager.RmSystem"),
    mainClass in assembly := Some("resourcemanager.RmSystem"),
    assemblyJarName in assembly := "resourcemanager.jar",
    libraryDependencies ++= Dependencies.runtimeDependencies
  )

lazy val driver = (project in file("runtime"))
  .settings(runtimeSettings: _*)
  .settings(
    target := file("runtime/drivertarget"),
    mainClass in (Compile, run) := Some("driver.DriverSystem"),
    mainClass in assembly := Some("driver.DriverSystem"),
    assemblyJarName in assembly := "driver.jar",
    libraryDependencies ++= Dependencies.runtimeDependencies
  )

lazy val root = (project in file("."))
  .aggregate(taskmanager, driver, resourcemanager)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*) // apply the default settings
  .settings(
    parallelExecution in Test := false // do not run test cases in parallel
)
