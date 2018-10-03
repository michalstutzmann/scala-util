package com.github.mwegrz.scalautil

import javax.crypto.Cipher
import javax.crypto.spec.{ IvParameterSpec, SecretKeySpec }
import org.bouncycastle.jce.provider.BouncyCastleProvider
import _root_.scodec.bits.ByteVector

package object crypto {
  private val Provider = new BouncyCastleProvider()
  private val ZeroIv = new IvParameterSpec(new Array[Byte](16))

  def aes128Cmac(key: ByteVector, data: ByteVector): ByteVector = {
    import com.github.mwegrz.scalautil.AesCmac
    val cmac = new AesCmac(new SecretKeySpec(key.toArray, "AES"))
    ByteVector.view(cmac.calculate(data.toArray))
  }

  // TODO: Finish implementation
  // Based on AES CMAC algorithm defined in RFC4493 (https://tools.ietf.org/html/rfc4493)
  private def aes128Cmac2(key: Array[Byte], data: Array[Byte]): Array[Byte] = {
    def msb(data: Array[Byte]): Byte = {
      require(data.length > 0)
      (data(0) >>> 7).toByte
    }

    def shiftBitsLeftBy1(data: Array[Byte]): Array[Byte] = {
      def msb(byte: Byte): Byte = (byte & 128).toByte
      var carry: Byte = if (data.length > 1) msb(data(1)) else 0
      val result = new Array[Byte](data.length)
      for (i <- data.indices) {
        result(i) = (data(i) << 1 | carry).toByte
        carry = msb(data(i))
      }
      result
    }

    def xor(a: Array[Byte], b: Array[Byte]): Array[Byte] = {
      require(a.length == b.length)
      a.zip(b) map { case (i, j) => (i ^ j).toByte }
    }

    def aes128(key: Array[Byte], data: Array[Byte]): Array[Byte] = {
      val cipher = Cipher.getInstance("AES_128/CBC/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE,
                  new SecretKeySpec(key, "AES"),
                  new IvParameterSpec(new Array[Byte](16)))
      cipher.doFinal(data)
    }

    def generateSubKey(k: Array[Byte]): (Array[Byte], Array[Byte]) = {
      val ConstZero = new Array[Byte](16)
      val ConstRb = new Array[Byte](15) ++ Array(0x87).map(_.toByte)
      val l = aes128(k, ConstZero)
      val k1 =
        if (msb(l) == 0) shiftBitsLeftBy1(l)
        else xor(shiftBitsLeftBy1(l), ConstRb)
      val k2 =
        if (msb(k1) == 0) shiftBitsLeftBy1(k1)
        else xor(shiftBitsLeftBy1(k1), ConstRb)
      (k1, k2)
    }

    /*//+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
   //+                   Algorithm AES-CMAC                              +
   //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
   +                                                                   +
   +   Input    : K    ( 128-bit key )                                 +
   +            : M    ( message to be authenticated )                 +
   +            : len  ( length of the message in octets )             +
   +   Output   : T    ( message authentication code )                 +
   +                                                                   +
   +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
   +   Constants: const_Zero is 0x00000000000000000000000000000000     +
   +              const_Bsize is 16                                    +
   +                                                                   +
   +   Variables: K1, K2 for 128-bit subkeys                           +
   +              M_i is the i-th block (i=1..ceil(len/const_Bsize))   +
   +              M_last is the last block xor-ed with K1 or K2        +
   +              n      for number of blocks to be processed          +
   +              r      for number of octets of last block            +
   +              flag   for denoting if last block is complete or not +
   +                                                                   +
   +   Step 1.  (K1,K2) := Generate_Subkey(K);                         +
   +   Step 2.  n := ceil(len/const_Bsize);                            +
   +   Step 3.  if n = 0                                               +
   +            then                                                   +
   +                 n := 1;                                           +
   +                 flag := false;                                    +
   +            else                                                   +
   +                 if len mod const_Bsize is 0                       +
   +                 then flag := true;                                +
   +                 else flag := false;                               +
   +                                                                   +
   +   Step 4.  if flag is true                                        +
   +            then M_last := M_n XOR K1;                             +
   +            else M_last := padding(M_n) XOR K2;                    +
   +   Step 5.  X := const_Zero;                                       +
   +   Step 6.  for i := 1 to n-1 do                                   +
   +                begin                                              +
   +                  Y := X XOR M_i;                                  +
   +                  X := AES-128(K,Y);                               +
   +                end                                                +
   +            Y := M_last XOR X;                                     +
   +            T := AES-128(K,Y);                                     +
   +   Step 7.  return T;                                              +
   +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
     */

    val k = key
    val m = data
    val len = m.length
    val ConstZero = new Array[Byte](16)
    val ConstBsize = 16
    val (k1, k2) = generateSubKey(key)
    val ms: Array[Byte] = ???
    val mLast: Byte = ???
    var n = Math.ceil(len.toDouble / ConstBsize).toInt
    var r: Int = ???
    var flag: Boolean = ???

    if (n == 0) {
      n = 1
      flag = false
    } else {
      flag = len % ConstBsize == 0
    }

    if (flag) {}

    Array.empty[Byte]
  }

  def aes128EcbEncrypt(key: ByteVector,
                       data: ByteVector,
                       padding: Padding = Padding.`NoPadding`): ByteVector = {
    val cipher = Cipher.getInstance(s"AES/ECB/$padding", Provider)
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.toArray, "AES"))
    ByteVector.view(cipher.doFinal(data.toArray, 0, data.length.toInt))
  }

  def aes128EcbDecrypt(key: ByteVector,
                       data: ByteVector,
                       padding: Padding = Padding.`NoPadding`): ByteVector = {
    val cipher = Cipher.getInstance(s"AES/ECB/$padding", Provider)
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.toArray, "AES"))
    ByteVector.view(cipher.doFinal(data.toArray, 0, data.length.toInt))
  }

  def aes128CbcEncrypt(key: ByteVector,
                       data: ByteVector,
                       padding: Padding = Padding.`NoPadding`,
                       iv: InitializationVector = InitializationVector.Zero): ByteVector = {
    val cipher = Cipher.getInstance(s"AES/CBC/$padding", Provider)
    cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.toArray, "AES"),
                new IvParameterSpec(iv.toArray))
    ByteVector.view(cipher.doFinal(data.toArray, 0, data.length.toInt))
  }

  def aes128CbcDecrypt(key: ByteVector,
                       data: ByteVector,
                       padding: Padding = Padding.`NoPadding`,
                       iv: InitializationVector = InitializationVector.Zero): ByteVector = {
    val cipher = Cipher.getInstance(s"AES/CBC/$padding", Provider)
    cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key.toArray, "AES"),
                new IvParameterSpec(iv.toArray))
    ByteVector.view(cipher.doFinal(data.toArray, 0, data.length.toInt))
  }

  sealed trait Padding

  object Padding {
    case object `NoPadding` extends Padding {
      override def toString: String = "NoPadding"
    }
    case object `PKCS7Padding` extends Padding {
      override def toString: String = "PKCS7Padding"
    }
  }

  sealed trait InitializationVector {
    def toArray: Array[Byte]
  }

  object InitializationVector {
    case object Zero extends InitializationVector {
      override def toString: String = "NoPadding"

      override def toArray: Array[Byte] = new Array[Byte](16)
    }

    case class NonZero(bytes: ByteVector) extends InitializationVector {
      require(bytes.length == 16, s"expected byte vector of size 16 but got ${bytes.length}")

      override def toArray: Array[Byte] = bytes.toArray
    }
  }
}
