package com.github.mwegrz.scalautil.circe

import java.time.ZoneId
import java.util.Base64

import akka.http.scaladsl.model.Uri
import com.github.mwegrz.scalautil.CaseClass1Rep
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import javax.mail.internet.InternetAddress
import scodec.bits.{ BitVector, ByteVector }
import shapeless.{ ::, Generic, HNil, Lazy }

object codecs {
  implicit def circeValueClassEncoder[CC <: AnyVal, A](
      implicit rep: CaseClass1Rep[CC, A],
      delegate: Encoder[A]
  ): Encoder[CC] =
    delegate.contramap(rep.unapply)

  implicit def circeValueClassFromValue[CC <: AnyVal, B](
      implicit rep: CaseClass1Rep[CC, B],
      delegate: Decoder[B]
  ): Decoder[CC] =
    delegate.map { rep.apply }

  implicit val CirceByteVectorEncoder: Encoder[ByteVector] = Encoder.encodeString.contramap(_.toHex)
  implicit val CirceByteVectorDecoder: Decoder[ByteVector] =
    Decoder.decodeString.map(
      value => ByteVector.fromHex(value).getOrElse(throw new IllegalArgumentException(s"Invalid hex value: $value"))
    )

  implicit val CirceBitVectorEncoder: Encoder[BitVector] = Encoder.encodeString.contramap(_.toBin)
  implicit val CirceBitVectorDecoder: Decoder[BitVector] =
    Decoder.decodeString.map(
      value => BitVector.fromBin(value).getOrElse(throw new IllegalArgumentException(s"Invalid bin value: $value"))
    )

  implicit val CirceZoneIdEncoder: Encoder[ZoneId] = Encoder.encodeString.contramap(_.getId)
  implicit val CirceZoneIdDecoder: Decoder[ZoneId] = Decoder.decodeString.map(ZoneId.of)

  implicit val CirceUriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val CirceUriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit val CirceInternetAddressEncoder: Encoder[InternetAddress] = Encoder.encodeString.contramap(_.toString)
  implicit val CirceInternetAddressDecoder: Decoder[InternetAddress] =
    Decoder.decodeString.map(InternetAddress.parse(_).head)

  implicit def circeStringValueClassKeyEncoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: Ref <:< (String :: HNil)
  ): KeyEncoder[Key] = key => generic.value.to(key).head

  implicit def circeStringValueClassKeyDecoder[Key, Ref](
      implicit generic: Lazy[Generic.Aux[Key, Ref]],
      evidence: (String :: HNil) =:= Ref
  ): KeyDecoder[Key] =
    string => Some(generic.value.from(string :: HNil))

  implicit def circeByteArrayKeyEncoder: KeyEncoder[Array[Byte]] = Base64.getEncoder.encodeToString

  implicit def circeByteArrayKeyDecoder: KeyDecoder[Array[Byte]] =
    string => Some(Base64.getDecoder.decode(string))
}
