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


lazy val statemanager = (project in file("runtime/statemanager"))
  .dependsOn(runtimeProtobuf, runtimeCommon % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.statemanager)
  .settings(modname("runtime.statemanager"))
  .settings(Assembly.settings("runtime.statemanager.SmSystem", "statemanager.jar"))
  .settings(Sigar.loader())


lazy val appmanager = (project in file("runtime/appmanager"))
  .dependsOn(runtimeProtobuf, runtimeCommon % "test->test; compile->compile")
  .settings(runtimeSettings: _*)
  .settings(Dependencies.appmanager)
  .settings(modname("runtime.appmanager"))
  .settings(Assembly.settings("runtime.appmanager.AmSystem", "appmanager.jar"))
  .settings(Sigar.loader())

lazy val runtimeProtobuf = (project in file("runtime-protobuf"))
  .settings(runtimeSettings: _*)
  .settings(Dependencies.protobuf)
  .settings(modname("runtime.protobuf"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

lazy val runtimeCommon = (project in file("runtime-common"))
  .settings(runtimeSettings: _*)
  .settings(Dependencies.runtimeCommon)
  .settings(modname("runtime.common"))

lazy val root = (project in file("."))
  .aggregate(statemanager, appmanager, runtimeProtobuf, runtimeCommon)


def modname(m: String): Def.SettingsDefinition = {
  val mn = "Module"
  packageOptions in (Compile, packageBin) += Package.ManifestAttributes(mn → m)
}
