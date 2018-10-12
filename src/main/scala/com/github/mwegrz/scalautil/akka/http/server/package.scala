package com.github.mwegrz.scalautil.akka.http

import akka.http.scaladsl.server.{ PathMatcher, PathMatcher1 }
import shapeless.{ ::, Generic, HNil, Lazy }

package object server {
  implicit def stringValueClassPathMatcher[ValueClass]: PathMatcher1[ValueClass] = {
    implicit def create[Ref](implicit generic: Lazy[Generic.Aux[ValueClass, Ref]],
                             evidence: (String :: HNil) =:= Ref): PathMatcher1[ValueClass] =
      PathMatcher("""\d+""".r) map { value =>
        generic.value.from(value :: HNil)
      }
    implicitly[PathMatcher1[ValueClass]]
  }
}
