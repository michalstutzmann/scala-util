package com.github.mwegrz.scalautil.scalatest

import org.scalatest.{ FunSpec, GivenWhenThen }
import org.scalatest.prop.PropertyChecks

class TestSpec extends FunSpec with GivenWhenThen with PropertyChecks
