package com.github.mwegrz.scalautil.circe

import akka.http.scaladsl.model.Uri
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import scodec.bits.ByteVector
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit def caseClass1Encoder[Wrapper, Ref, Value](
      implicit generic: Lazy[Generic.Aux[Wrapper, Ref]],
      evidence: Ref <:< (Value :: HNil),
      encoder: Encoder[Value]): Encoder[Wrapper] =
    Encoder.instance { value ⇒
      encoder(generic.value.to(value).head)
    }

  implicit def caseClass1Decoder[Wrapper, Ref, Value](implicit g: Lazy[Generic.Aux[Wrapper, Ref]],
                                                      evidence: (Value :: HNil) =:= Ref,
                                                      decoder: Decoder[Value]): Decoder[Wrapper] =
    Decoder.instance { cursor ⇒
      decoder(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  implicit val ByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toBase64)
  implicit val ByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(ByteVector.fromBase64(_).get)

  implicit val UriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val UriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def wrappedStringMapKeyEncoder[KeyWrapper, Ref](
      implicit generic: Lazy[Generic.Aux[KeyWrapper, Ref]],
      evidence: Ref <:< (String :: HNil)): KeyEncoder[KeyWrapper] =
    new KeyEncoder[KeyWrapper] {
      override def apply(key: KeyWrapper): String = generic.value.to(key).head
    }

  implicit def wrappedStringMapKeyDecoder[KeyWrapper, Ref](
      implicit generic: Lazy[Generic.Aux[KeyWrapper, Ref]],
      evidence: (String :: HNil) =:= Ref): KeyDecoder[KeyWrapper] =
    new KeyDecoder[KeyWrapper] {
      override def apply(string: String): Option[KeyWrapper] =
        Some(generic.value.from(string :: HNil))
    }
}
