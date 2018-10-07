package com.github.mwegrz.scalautil.akka
import akka.http.scaladsl.unmarshalling.Unmarshaller
import shapeless.{ ::, Generic, HNil, Lazy }

import scala.concurrent.Future

package object http {
  implicit def stringValUnmarshaller[A](
      implicit g: Lazy[Generic.Aux[A, String :: HNil]]): Unmarshaller[String, A] =
    createUnmarshaller(value => g.value.from(value :: HNil))

  def createUnmarshaller[A, B](f: A => B): Unmarshaller[A, B] =
    Unmarshaller(_ => a => Future.successful(f(a)))
}
