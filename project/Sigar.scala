import sbt._
import Keys._

object Sigar {
  def loader() = {
    javaOptions in Test += s"-Djava.library.path=native/"
  }
}
