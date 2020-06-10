package com.github.mwegrz.scalautil.akka.http

import akka.http.scaladsl.marshalling.Marshaller
import shapeless.{ ::, Generic, HNil, Lazy }

object marshalling {
  implicit def valueClassMarshaller[ValueClass, Ref, Value, A](implicit
      generic: Lazy[Generic.Aux[ValueClass, Ref]],
      evidence: Ref <:< (Value :: HNil),
      marshaller: Marshaller[Value, A]
  ): Marshaller[ValueClass, A] =
    marshaller.compose { value => generic.value.to(value).head }
}
