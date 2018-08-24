package clustermanager.standalone.taskmanager.isolation

import java.nio.file.{Files, Paths}
import java.util.Comparator
import java.util.stream.Collectors

import clustermanager.standalone.taskmanager.utils.{ContainerUtils, TaskManagerConfig}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import runtime.protobuf.messages.{ArcTask, Container}

class LinuxContainerEnvSpec extends FlatSpec with BeforeAndAfterAll with TaskManagerConfig with Cgroups {
  private val path = cgroupsPath
  private val rootPath = Paths.get(path)
  private var lce: Either[Throwable, LinuxContainerEnvironment] = _

  private val taskOneName = "map"
  private val taskTwoName = "filter"
  private val taskOne = ArcTask(taskOneName, 1, 1024)
  private val taskTwo= ArcTask(taskTwoName, 2, 2024)
  private val testTasks = Seq(taskOne, taskTwo)
  private val containerId = "containerz"
  private val testContainer = Container(containerId, "jobId", tasks = testTasks)

  override protected def beforeAll(): Unit = {
    cgroupSetup()
    lce = LinuxContainerEnvironment()
  }

  override protected def afterAll(): Unit = {
    cleanSetup()
  }

  "LCE" should "initialize correctly" in {
    lce match {
      case Right(cg) =>
        val expectedBytes = cg.getMemory
        val readBytes = readToLong(Paths.get(containersMem + "/" + HARD_MEMORY_LIMIT)).get
        assert(expectedBytes == readBytes)

        val expectedCpuQuota = CFS_PERIOD_VALUE * cg.getCores
        val readQuota = readToLong(Paths.get(containersCpu + "/" + CPU_CFS_QUOTA)).get
        assert(expectedCpuQuota == readQuota)
      case Left(e) => fail
    }
  }

  "LCE" should "create container's correctly" in {
    lce match {
      case Right(cg) =>
        assert(cg.createContainerGroup(testContainer))
        assert(Files.exists(Paths.get(containersCpu + "/" + containerId)))
        assert(Files.exists(Paths.get(containersMem + "/" + containerId)))

        // CPU check
        val containerCores = testContainer.tasks.foldLeft(0)(_ + _.cores)
        val expectedContainerShares = (1024 * (containerCores / cg.getCores.toDouble)).toInt
        val readContainerShares = readToLong(Paths.get(containersCpu + "/" + containerId + "/" + CPU_SHARES)).get
        assert(expectedContainerShares == readContainerShares)

        // Task 1 shares
        val taskOneShares = readToLong(Paths.get(containersCpu + "/" + containerId + "/" + taskOneName + "/" + CPU_SHARES)).get
        val expectedTaskOneShares = (1024 * (taskOne.cores / containerCores.toDouble)).toInt
        assert(taskOneShares == expectedTaskOneShares)

        // Task 2 shares
        val taskTwoShares = readToLong(Paths.get(containersCpu + "/" + containerId + "/" + taskTwoName + "/" + CPU_SHARES)).get
        val expectedTaskTwoShares = (1024 * (taskTwo.cores / containerCores.toDouble)).toInt
        assert(taskTwoShares == expectedTaskTwoShares)


        // Memory check
        val containerMemory = testContainer.tasks.foldLeft(0)(_ + _.memory)
        val readMemory = readToLong(Paths.get(containersMem + "/" + containerId + "/" + SOFT_MEMORY_LIMIT)).get
        assert(readMemory == ContainerUtils.mbToBytes(containerMemory))

        // Task 1 memory
        val taskOneMem = taskOne.memory
        val taskOneMemRead = readToLong(Paths.get(containersMem + "/" + containerId + "/" + taskOneName + "/" + SOFT_MEMORY_LIMIT)).get
        assert(ContainerUtils.mbToBytes(taskOneMem) == taskOneMemRead)

        // Task 2 memory
        val taskTwoMem = taskTwo.memory
        val taskTwoMemRead = readToLong(Paths.get(containersMem + "/" + containerId + "/" + taskTwoName + "/" + SOFT_MEMORY_LIMIT)).get
        assert(ContainerUtils.mbToBytes(taskTwoMem) == taskTwoMemRead)
      case Left(_) => fail
    }
  }





  private def cgroupSetup(): Unit = {
    if (!Files.isDirectory(rootPath))
      Files.createDirectory(rootPath)

    Files.createDirectory(Paths.get(rootPath + "/cpu"))
    Files.createDirectory(Paths.get(rootPath + "/memory"))

    Files.createDirectory(Paths.get(containersCpu))
    Files.createDirectory(Paths.get(containersMem))

    Files.createFile(Paths.get(containersMem + "/" + HARD_MEMORY_LIMIT))
    Files.createFile(Paths.get(containersCpu + "/" + CPU_CFS_QUOTA))
  }


  private def cleanSetup(): Unit = {
    import scala.collection.JavaConverters._

    // Make sure everything gets cleaned
    val toDelete = Files.walk(rootPath)
      .sorted(Comparator.reverseOrder())
      .collect(Collectors.toList())
      .asScala

    toDelete.foreach(Files.deleteIfExists(_))
  }

}
