package runtime.kompact


case class ExecutorTerminated(ref: KompactRef)
case class ExecutorUp(ref: KompactRef)
case object ProxyServerTerminated
