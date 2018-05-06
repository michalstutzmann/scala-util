package com.github.mwegrz.scalautil.circe

import akka.http.scaladsl.model.Uri
import com.github.mwegrz.scalautil.StringVal
import io.circe.{ Decoder, Encoder, KeyDecoder, KeyEncoder }
import io.circe.java8.time.TimeInstances
import io.circe.syntax._
import io.circe.parser._
import shapeless.{ :+:, ::, CNil, Coproduct, Generic, HNil, Inl, Inr, LabelledGeneric, Lazy, Witness }
import shapeless.labelled._

object codecs extends TimeInstances {
  implicit def encodeAnyVal[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], e: Encoder[V]): Encoder[T] =
    Encoder.instance { value ⇒
      e(g.value.to(value).head)
    }

  implicit def decodeAnyVal[T <: AnyVal, V](implicit g: Lazy[Generic.Aux[T, V :: HNil]], d: Decoder[V]): Decoder[T] =
    Decoder.instance { cursor ⇒
      d(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  implicit def deriveStringValUnwrappedEncoder[T <: StringVal](implicit g: Lazy[Generic.Aux[T, String :: HNil]],
                                                               e: Encoder[String]): Encoder[T] =
    Encoder.instance { value ⇒
      e(g.value.to(value).head)
    }

  implicit def deriveStringValUnwrappedDecoder[T <: StringVal](implicit g: Lazy[Generic.Aux[T, String :: HNil]],
                                                               d: Decoder[String]): Decoder[T] =
    Decoder.instance { cursor ⇒
      d(cursor).map { value ⇒
        g.value.from(value :: HNil)
      }
    }

  /*trait IsEnum[C <: Coproduct] {
    def to(c: C): String
    def from(s: String): Option[C]
  }

  object IsEnum {
    implicit val cnilIsEnum: IsEnum[CNil] = new IsEnum[CNil] {
      def to(c: CNil): String = sys.error("Impossible")
      def from(s: String): Option[CNil] = None
    }

    implicit def cconsIsEnum[K <: Symbol, H <: Product, T <: Coproduct](implicit witK: Witness.Aux[K],
                                                                        witH: Witness.Aux[H],
                                                                        gen: Generic.Aux[H, HNil],
                                                                        tie: IsEnum[T]): IsEnum[FieldType[K, H] :+: T] =
      new IsEnum[FieldType[K, H] :+: T] {
        def to(c: FieldType[K, H] :+: T): String = c match {
          case Inl(h) => witK.value.name
          case Inr(t) => tie.to(t)
        }

        def from(s: String): Option[FieldType[K, H] :+: T] =
          if (s == witK.value.name) Some(Inl(field[K](witH.value)))
          else tie.from(s).map(Inr(_))
      }
  }

  implicit def encodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Encoder[A] =
    Encoder[String].contramap[A](a => rie.to(gen.to(a)))

  implicit def decodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Decoder[A] =
    Decoder[String].emap { s =>
      rie.from(s).map(gen.from).toRight("enum")
    }*/

  implicit val uriEncoder: Encoder[Uri] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[Uri] = Decoder.decodeString.map(Uri(_))

  implicit def encodeMapKey[A <: AnyRef](implicit encoder: Encoder[A]): KeyEncoder[A] = new KeyEncoder[A] {
    override def apply(key: A): String = key.asJson.toString
  }

  implicit def decodeMapKey[A <: AnyRef](implicit decoder: Decoder[A]): KeyDecoder[A] = new KeyDecoder[A] {
    override def apply(key: String): Option[A] = parse(key).toOption.flatMap(_.as[A].toOption)
  }
}
