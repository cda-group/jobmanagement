name := "jobmanagement." + "runtime"
logLevel in assembly := Level.Error


PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
