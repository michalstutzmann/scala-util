package com.github.mwegrz.scalautil.scodec

import java.security.Security

import javax.crypto.Cipher
import javax.crypto.spec.{ IvParameterSpec, SecretKeySpec }
import org.bouncycastle.jce.provider.BouncyCastleProvider
import scodec.{ Attempt, Codec, DecodeResult, Decoder, SizeBound }
import scodec.codecs.ascii
import scodec.bits.{ BitVector, ByteVector }

import scala.util.Try

object codecs {
  def asciiL: Codec[String] = ascii.xmap[String](_.reverse, _.reverse)

  def allBytesExceptLast[A](n: Int, codec: Codec[A]): Codec[A] =
    Codec(codec.asEncoder, Decoder[A] { bits: BitVector =>
      codec.decode(bits.dropRight(n * 8L)).map { _.copy(remainder = bits.takeRight(n * 8L)) }
    })

  def unboundedList[A](elementBitLength: Int, codec: Codec[A]): Codec[List[A]] =
    boundedList(None, elementBitLength, codec)

  def boundedList[A](bitLength: Int, elementBitLength: Int, codec: Codec[A]): Codec[List[A]] =
    boundedList(Some(bitLength), elementBitLength, codec)

  private def boundedList[A](bitLength: Option[Int], elementBitLength: Int, codec: Codec[A]): Codec[List[A]] =
    new Codec[List[A]] {
      override val sizeBound: SizeBound = bitLength.fold(SizeBound.unknown)(value => SizeBound.exact(value))

      override def encode(value: List[A]): Attempt[BitVector] = {
        val encode = Try(value.map(codec.encode).map(_.require))
        Attempt.fromTry(encode).map(_.foldLeft(BitVector.empty)((a, b) => a ++ b))
      }

      override def decode(bits: BitVector): Attempt[DecodeResult[List[A]]] = {
        val decode = Try(
          bits
            .grouped(elementBitLength)
            .map(codec.decode)
            .map(_.require)
            .toList
        )

        Attempt.fromTry(decode).map(value => DecodeResult(value.map(_.value), value.last.remainder))
      }
    }

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def aesCtrEncrypted[A](iv: ByteVector, key: ByteVector, codec: Codec[A]): Codec[A] =
    aesEncrypted(AesBlockMode.`CTR`, AesPadding.`NoPadding`, iv, key, codec)

  def aesEncrypted[A](
      mode: AesBlockMode,
      padding: AesPadding,
      iv: ByteVector,
      key: ByteVector,
      codec: Codec[A]
  ): Codec[A] =
    new Codec[A] {
      override def sizeBound: SizeBound = codec.sizeBound

      override def encode(value: A): Attempt[BitVector] = {
        codec.encode(value).flatMap { bits =>
          val encrypt = cipher(Cipher.ENCRYPT_MODE, bits.toByteVector)
          Attempt.fromTry(encrypt).map(_.toBitVector)
        }
      }

      override def decode(bits: BitVector): Attempt[DecodeResult[A]] = {
        val decrypt = cipher(Cipher.DECRYPT_MODE, bits.toByteVector)
        Attempt.fromTry(decrypt).flatMap(bytes => codec.decode(bytes.toBitVector))
      }

      private def cipher(cipherMode: Int, bytes: ByteVector): Try[ByteVector] = Try {
        val secretKeySpec = new SecretKeySpec(key.toArray, "AES")
        val aes = Cipher.getInstance(s"AES/$mode/$padding", BouncyCastleProvider.PROVIDER_NAME)
        aes.init(cipherMode, secretKeySpec, new IvParameterSpec(iv.toArray))
        ByteVector(aes.doFinal(bytes.toArray))
      }
    }

  object AesPadding {
    case object `NoPadding` extends AesPadding {
      override def toString: String = "NoPadding"
    }
    case object `PKCS7Padding` extends AesPadding {
      override def toString: String = "PKCS7Padding"
    }
  }

  sealed trait AesPadding

  object AesBlockMode {
    case object `CTR` extends AesBlockMode {
      override def toString: String = "CTR"
    }

    case object `ECB` extends AesBlockMode {
      override def toString: String = "ECB"
    }

    case object `CBC` extends AesBlockMode {
      override def toString: String = "CBC"
    }
  }

  sealed trait AesBlockMode
}
