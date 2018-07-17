package clustermanager.standalone

import clustermanager.standalone.resourcemanager.RmSystem
import clustermanager.standalone.taskmanager.TmSystem


/**
  * To be improved.
  * Add more advanced options.
  * Perhaps add scopt as CLI parser.
  */
object Standalone extends App {

  if (args.length > 0) {
    val mode = args(0)
    mode match {
      case "--taskmanager" =>
        startTaskManager()
      case "--resourcemanager" =>
        startResourceManager()
      case _ =>
        help()
    }
  } else {
    help()
  }

  private def help(): Unit = {
    println("--taskmanager or --resourcemanager")
  }

  private def startResourceManager(): Unit = {
    new RmSystem().start()
  }

  private def startTaskManager(): Unit = {
    new TmSystem().start()
  }


}
