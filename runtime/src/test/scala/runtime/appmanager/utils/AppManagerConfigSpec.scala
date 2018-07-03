package runtime.appmanager.utils

import java.net.InetAddress

import runtime.BaseSpec

import scala.util.{Failure, Success, Try}

class AppManagerConfigSpec extends BaseSpec with AppManagerConfig {

  "AppManager Config" should "have functional config" in {
    assert(config.isResolved)
    assert(restPort >= 0 && restPort <= 65535)
    assert(appMasterKeepAlive > 0 && appMasterKeepAlive <= 20000)
    Try (InetAddress.getByName(interface)) match {
      case Success(s) => succeed
      case Failure(_) => fail
    }
  }
}