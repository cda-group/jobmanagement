name := "jobmanagement." + "root"

lazy val generalSettings = Seq(
  // can be changed
  organization := "se.sics.cda.jm",
  scalaVersion := "2.12.6"
)

lazy val workerSettings = generalSettings ++ Seq(
  fork in run := true,  // https://github.com/sbt/sbt/issues/3736#issuecomment-349993007
  cancelable in Global := true,
  version := "0.1"
)

// Not relevant right now
lazy val monitorSettings = Seq(

)

lazy val resourcemanagerSettings = generalSettings ++ Seq(
  fork in run := true,  // https://github.com/sbt/sbt/issues/3736#issuecomment-349993007
  cancelable in Global := true,
  version := "0.1"
)

lazy val driverSettings = generalSettings ++ Seq(
  fork in run := true,  // https://github.com/sbt/sbt/issues/3736#issuecomment-349993007
  cancelable in Global := true,
  version := "0.1"
)

lazy val commonSettings = generalSettings ++ Seq(
  version := "0.1"
)

lazy val worker = (project in file("worker"))
  .dependsOn(common % "test->test;compile->compile")
  .settings(workerSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.workerDependencies
  )

lazy val resourcemanager = (project in file("resourcemanager"))
  .dependsOn(common % "test->test;compile->compile")
  .settings(resourcemanagerSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.resourcemanagerDependencies
  )

lazy val driver = (project in file("driver"))
  .dependsOn(common % "test->test;compile->compile")
  .settings(driverSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.driverDependencies
  )
lazy val common = (project in file("common"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.commonDependencies
  )

lazy val root = (project in file("."))
  .aggregate(worker, driver, resourcemanager, common)
