package com.github.mwegrz.scalautil

import _root_.scodec.bits.ByteVector

sealed trait Wrapper[A]

trait ByteWrapper extends Wrapper[Byte]

trait ShortWrapper extends Wrapper[Short]

trait IntWrapper extends Wrapper[Int]

trait LongWrapper extends Wrapper[Long]

trait FloatWrapper extends Wrapper[Float]

trait DoubleWrapper extends Wrapper[Double]

trait StringWrapper extends Wrapper[String]

trait BigDecimalWrapper extends Wrapper[BigDecimal]

trait ByteVectorWrapper extends Wrapper[ByteVector]
