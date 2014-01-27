package magenta

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class HostListTests extends FlatSpec with ShouldMatchers {
  import HostList._
  
  it should "provide an alphabetical list of all hosts and supported apps" in {
    val hostList = List(Host("z.z.z.z", Set(StackApp("stack", "z"))), Host("x.x.x.x", Set(LegacyApp("x"))))
    hostList.dump should be (" x.x.x.x: x\n z.z.z.z: stack::z")
  }
}