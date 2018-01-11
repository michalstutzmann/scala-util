package com.github.mwegrz.scalautil.slf4j.bridge

import org.slf4j.bridge.SLF4JBridgeHandler._

trait Slf4jBridgeHandling {
  removeHandlersForRootLogger()
  install()
}
