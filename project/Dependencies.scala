
import sbt._

object Dependencies {
  //TODO check versions
  val scalatestVersion = "3.0.1"
  val loggingVersion = "3.5.0"
  val akkaVersion = "2.5.12"
  val logbackVersion = "1.2.3"
  val typeConfigVersion = "1.3.1"


  val testDependencies: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )

  val logDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % loggingVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion
  )
  val confDependencies: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % typeConfigVersion
  )


  val akkaDependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  )

  val simpleAkka: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
  )


  // Common libs that are used together
  val basic : Seq[ModuleID] =
    logDependencies ++ confDependencies ++ testDependencies


  val taskmanagerDependencies: Seq[ModuleID] = basic ++ akkaDependencies
  val driverDependencies: Seq[ModuleID] = basic ++ akkaDependencies
  val resourcemanagerDependencies: Seq[ModuleID] = basic ++ akkaDependencies
  val commonDependencies: Seq[ModuleID] = basic ++ simpleAkka


}