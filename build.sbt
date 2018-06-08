name := "jobmanagement." + "root"

lazy val generalSettings = Seq(
  // can be changed
  organization := "se.sics.cda.jm",
  scalaVersion := "2.12.6"
)

lazy val taskManagerSettings = generalSettings ++ Seq(
  version := "0.1"
)

// Not relevant right now
lazy val monitorSettings = Seq(

)

lazy val driverExecSettings = generalSettings ++ Seq(
  version := "0.1"
)

lazy val commonSettings = generalSettings ++ Seq(
  version := "0.1"
)

lazy val taskmanager = (project in file("taskmanager"))
  .dependsOn(common % "test->test;compile->compile")
  .settings(taskManagerSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.taskmanagerDependencies
  )

lazy val driverexec = (project in file("driverexec"))
  .dependsOn(common % "test->test;compile->compile")
  .settings(driverExecSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.driverexecDependencies
  )
lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  //.settings()

lazy val root = (project in file("."))
  .aggregate(taskmanager, driverexec)
  //.aggregate(taskmanager, driverexec, common)
