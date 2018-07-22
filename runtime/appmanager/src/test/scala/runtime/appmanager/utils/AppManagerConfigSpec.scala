package runtime.appmanager.utils

import java.net.InetAddress

import org.scalatest.FlatSpec

import scala.util.{Failure, Success, Try}

class AppManagerConfigSpec extends FlatSpec with AppManagerConfig {

  "AppManager Config" should "be functional" in {
    assert(config.isResolved)
    assert(restPort >= 0 && restPort <= 65535)
    assert(appMasterKeepAlive > 0 && appMasterKeepAlive <= 20000)
    Try (InetAddress.getByName(interface)) match {
      case Success(s) => succeed
      case Failure(_) => fail
    }
  }
}
