package runtime.driver.utils

import java.net.InetAddress

import runtime.BaseSpec

import scala.util.{Failure, Success, Try}

class DriverConfigSpec extends BaseSpec with DriverConfig  {

  "Driver Config" should "have functional config" in {
    assert(config.isResolved)
    assert(restPort >= 0 && restPort <= 65535)
    assert(jobManagerKeepAlive > 0 && jobManagerKeepAlive <= 20000)
    Try (InetAddress.getByName(interface)) match {
      case Success(s) => succeed
      case Failure(_) => fail
    }
  }
}
