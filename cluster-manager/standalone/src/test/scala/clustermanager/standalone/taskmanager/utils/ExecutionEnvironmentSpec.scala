package clustermanager.standalone.taskmanager.utils

import java.nio.file.{Files, Paths}

import clustermanager.common.executor.ExecutionEnvironment
import org.scalatest.FlatSpec

import scala.util.{Failure, Success}

class ExecutionEnvironmentSpec extends FlatSpec {
  val appId = "testappdid"
  val env = new ExecutionEnvironment(appId)

  "Execution Environment" should "be created" in {
    env.create() match {
      case Success(_) =>
        assert(Files.exists(Paths.get(env.getAppPath)))
      case Failure(_) =>
        assert(fail)
    }
  }

  "Binary" should "be made executable" in {
    val binary = Files.write(Paths.get(env.getAppPath + "/" + appId), "testdata".getBytes())
    env.writeBinaryToFile(appId, Files.readAllBytes(binary))
    assert(Files.isExecutable(binary))
  }

  "Execution Environment" should "be cleaned" in {
    env.clean()
    assert(!Files.exists(Paths.get(env.getAppPath)))
  }

}
