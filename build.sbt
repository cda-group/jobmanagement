import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "jobmanagement." + "root"

def sysPropOrDefault(propName:String,default:String): String =
  Option(System.getProperty(propName)).getOrElse(default)


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

lazy val runtime = (project in file("runtime"))
  .settings(runtimeSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.runtimeDependencies,
    mainClass in assembly := Some(sysPropOrDefault("runtimeClass", "runtime.resourcemanager.RmSystem")),
    assemblyJarName in assembly := sysPropOrDefault("runtimeJar", "resourcemanager.jar")
  )

lazy val root = (project in file("."))
  .aggregate(runtime)
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*) // apply the default settings
  .settings(
    parallelExecution in Test := false // do not run test cases in parallel
)
