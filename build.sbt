import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "jobmanagement." + "root"


lazy val sigarFolder = SettingKey[File]("sigar-folder", "Location of native library extracted by Sigar java agent.")

def sysPropOrDefault(propName:String,default:String): String =
  Option(System.getProperty(propName)).getOrElse(default)


lazy val generalSettings = Seq(
  // can be changed
  organization := "se.sics.cda",
  scalaVersion := "2.12.6"
)

lazy val sigarSettings = Seq(
  //TODO fix
  javaOptions in Test += s"-Djava.library.path=${"../target/native/taskmanager"}",
)

lazy val runtimeSettings = generalSettings ++ sigarSettings ++ Seq(
  fork in run := true,  // https://github.com/sbt/sbt/issues/3736#issuecomment-349993007
  cancelable in Global := true,
  version := "0.1",
  javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:+CMSClassUnloadingEnabled"),
  fork in Test := true
  //assemblyMergeStrategy in assembly := {
  //  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  //  case PathList("application.conf") => MergeStrategy.discard
  // case x => MergeStrategy.first
  //}
)

lazy val runtimeMultiJvmSettings = multiJvmSettings ++ Seq(
  // For loading Sigar
  jvmOptions in MultiJvm += s"-Djava.library.path=${"target/native"}"
)

lazy val runtime = (project in file("runtime"))
  .settings(runtimeSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.runtimeDependencies,
    mainClass in assembly := Some(sysPropOrDefault("runtimeClass", "runtime.resourcemanager.RmSystem")),
    assemblyJarName in assembly := sysPropOrDefault("runtimeJar", "resourcemanager.jar"),
    test in assembly := {},
    parallelExecution in Test := false // do not run test cases in parallel
  )
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(runtimeMultiJvmSettings: _*)

lazy val root = (project in file("."))
  .aggregate(runtime)

