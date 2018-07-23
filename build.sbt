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


lazy val root = (project in file("."))
  .aggregate(statemanager, appmanager, runtimeProtobuf,
    runtimeCommon, runtimeTests, standalone,
    yarnUtils, yarnExecutor, yarnMaster, clusterManagerCommon)


lazy val statemanager = (project in file("runtime/statemanager"))
  .dependsOn(runtimeProtobuf, runtimeCommon % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.statemanager)
  .settings(moduleName("runtime.statemanager"))
  .settings(Assembly.settings("runtime.statemanager.SmSystem", "statemanager.jar"))
  .settings(Sigar.loader())


lazy val appmanager = (project in file("runtime/appmanager"))
  .dependsOn(runtimeProtobuf, runtimeCommon, yarnUtils % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.appmanager)
  .settings(moduleName("runtime.appmanager"))
  .settings(Assembly.settings("runtime.appmanager.AmSystem", "appmanager.jar"))
  .settings(Sigar.loader())


lazy val runtimeProtobuf = (project in file("runtime-protobuf"))
  .settings(runtimeSettings: _*)
  .settings(Dependencies.protobuf)
  .settings(moduleName("runtime.protobuf"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

lazy val runtimeCommon = (project in file("runtime-common"))
  .settings(runtimeSettings: _*)
  .settings(Dependencies.runtimeCommon)
  .settings(moduleName("runtime.common"))

lazy val runtimeTests = (project in file("runtime-tests"))
  .dependsOn(
    runtimeProtobuf, runtimeCommon % "test->test; compile->compile",
    statemanager, appmanager, standalone % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.runtimeTests)
  .settings(moduleName("runtime.tests"))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(Sigar.loader())
  .settings(
    parallelExecution in Test := false // do not run test cases in
  )
lazy val clusterManagerCommon = (project in file("cluster-manager-common"))
  .dependsOn(runtimeProtobuf % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.clusterManagerCommon)
  .settings(moduleName("clustermanager.common"))

lazy val standalone = (project in file("cluster-manager/standalone"))
  .dependsOn(runtimeProtobuf, runtimeCommon, clusterManagerCommon % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.standalone)
  .settings(moduleName("clustermanager.standalone"))
  .settings(Assembly.settings("clustermanager.standalone.Standalone", "standalone.jar"))
  .settings(Sigar.loader())

lazy val yarnUtils = (project in file("cluster-manager/yarn/utils"))
  .settings(runtimeSettings: _*)
  .settings(Dependencies.yarnUtils)
  .settings(moduleName("clustermanager.yarn.utils"))

lazy val yarnExecutor = (project in file("cluster-manager/yarn/taskexecutor"))
  .dependsOn(runtimeProtobuf, runtimeCommon, yarnUtils, clusterManagerCommon % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.yarnExecutor)
  .settings(moduleName("clustermanager.yarn.taskexecutor"))
  .settings(Assembly.settings("clustermanager.yarn.taskexecutor.TaskExecutorApplication", "yarn-taskexecutor.jar"))

lazy val yarnMaster = (project in file("cluster-manager/yarn/taskmaster"))
  .dependsOn(runtimeProtobuf, yarnUtils % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.yarnMaster)
  .settings(moduleName("clustermanager.yarn.taskmaster"))
  .settings(Assembly.settings("clustermanager.yarn.taskmaster.TaskMasterApplication", "yarn-taskmaster.jar"))

def moduleName(m: String): Def.SettingsDefinition = {
  val mn = "Module"
  packageOptions in (Compile, packageBin) += Package.ManifestAttributes(mn → m)
}
