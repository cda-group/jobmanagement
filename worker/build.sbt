name := "jobmanagement." + "worker"

mainClass in (Compile, run) := Some("worker.WorkerSystem")
