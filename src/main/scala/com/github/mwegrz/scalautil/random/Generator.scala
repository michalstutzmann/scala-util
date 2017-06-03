package com.github.mwegrz.scalautil.random

import java.security.SecureRandom

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.TreeMap
import scala.util.Random
import scala.language.higherKinds

trait Generator[+T] { self =>
  def generate(): T

  def generateGaussian(): T = throw new UnsupportedOperationException

  def map[S](f: T => S): Generator[S] = new Generator[S] {
    override def generate() = f(self.generate())

    override def generateGaussian() = f(self.generateGaussian())
  }

  def flatMap[S](f: T => Generator[S]): Generator[S] = new Generator[S] {
    override def generate() = f(self.generate()).generate()

    override def generateGaussian() =
      f(self.generateGaussian()).generateGaussian()
  }
}

object Generator {
  val integers = new Generator[Int] {
    // TODO: Seed
    val rand         = new SecureRandom
    val MaxMeanValue = Int.MaxValue / 2

    override def generate() = Math.abs(rand.nextInt())

    override def generateGaussian() = rand.nextGaussian().round.toInt

  }

  def integers(mean: Int, variance: Int) = new Generator[Int] {
    val rand = new Random

    override def generate() = rand.nextInt()

    override def generateGaussian() =
      (rand.nextGaussian() * scala.math.sqrt(variance) + mean).round.toInt

  }

  val longs = new Generator[Long] {
    val rand = new Random

    override def generate() = rand.nextLong()

    override def generateGaussian() = rand.nextGaussian().round

  }

  def longs(mean: Long, variance: Long) = new Generator[Long] {
    val rand = new Random

    override def generate() = rand.nextLong()

    override def generateGaussian() =
      (rand.nextGaussian() * scala.math.sqrt(variance) + mean).round

  }

  val floats = new Generator[Float] {
    val rand         = new Random
    val MaxMeanValue = Float.MaxValue / 2

    override def generate() = Math.abs(rand.nextFloat())

    override def generateGaussian() = rand.nextGaussian().toFloat

  }

  def floats(mean: Float, variance: Float) = new Generator[Float] {
    val rand = new Random

    override def generate() = rand.nextFloat()

    override def generateGaussian() =
      (rand.nextGaussian() * scala.math.sqrt(variance) + mean).toFloat

  }

  def single[T](x: T): Generator[T] = new Generator[T] {
    override def generate() = x

    override def generateGaussian() = generate()
  }

  def longsBetween(lowest: Long, highest: Long): Generator[Long] =
    for (x <- longs) yield lowest + Math.abs(x) % (highest - lowest)

  def integersBetween(lowest: Int, highest: Int): Generator[Int] =
    for (x <- integers) yield lowest + x % (highest - lowest)

  def booleans: Generator[Boolean] =
    for (x <- integers) yield x > 0 // Expands to `integers map (x => x > 0)`

  def pairs[T, U](t: Generator[T], u: Generator[U]): Generator[(T, U)] =
    for {
      x <- t
      y <- u
    } yield
      (x, y) // Expands to `def pairs[T, U](t: Generator[T], u: Generator[U]) = t flatMap (x => u map (y => (x, y)))`

  def oneOf[T](xs: T*): Generator[T] = {
    assert(xs.length > 0)
    for (idx <- integersBetween(0, xs.length)) yield xs(idx)
  }

  /*def frequencyOneOf[T](xs: (T, Int)*): Generator[T] = new Generator[T] {
    val random = new Random
    override def generate() = {
        var total = 0L
        val tree: TreeMap[Long, T] = {
          val builder = TreeMap.newBuilder[Long, T]
          xs.foreach {
            case (v, f) =>
              total += f
              builder.+=((total, v))
          }
          builder.result()
        }
        val value = (random.nextDouble() * total).toLong
        tree.from(value).head._2
    }

    override def generateGaussian() = throw new UnsupportedOperationException
  }*/

  def frequencyOneOf[T](xs: (T, Float)*): Generator[T] = new Generator[T] {
    val random = new Random
    override def generate() = {
      var total = 0.toFloat
      val tree: TreeMap[Float, T] = {
        val builder = TreeMap.newBuilder[Float, T]
        xs.foreach {
          case (v, f) =>
            total += f
            builder.+=((total, v))
        }
        builder.result()
      }
      val value = (random.nextDouble() * total).toFloat
      tree.from(value).head._2
    }

    override def generateGaussian() = throw new UnsupportedOperationException
  }

  def iteratedOneOf[T](xs: T*): Generator[T] = new Generator[T] {
    var index = 0

    override def generate() = {
      val current = xs(index)
      if (index < xs.size) index += 1
      current
    }

    override def generateGaussian() = throw new UnsupportedOperationException
  }

  def lists: Generator[List[Int]] =
    for {
      isEmpty <- booleans
      list    <- if (isEmpty) emptyLists else nonEmptyLists
    } yield list

  def emptyLists = single(Nil)

  def nonEmptyLists =
    for {
      head <- integers
      tail <- lists
    } yield head :: tail

  def withFrequency[T](list: List[T]): Generator[List[(T, Float)]] =
    frequencyLists(list.size).map { freqList =>
      freqList.zipWithIndex
        .map({ case (value, index) => (list(index), value) })
    }

  def frequencyLists[T](size: Int): Generator[List[Float]] = {
    def loop(size: Int, max: Int): Generator[List[Float]] = {
      assert(max >= 0 || max <= 100)

      if (size == 0) Generator.emptyLists
      else if (size == 1) Generator.single(List(max))
      else {
        for {
          freq <- Generator.integersBetween(0, max)
          list <- loop(size - 1, max - freq)
        } yield freq :: list
      }
    }

    loop(size, 100)
  }

  def byteLists(size: Int): Generator[List[Byte]] = {
    def loop(size: Int): Generator[List[Byte]] = {
      if (size == 0) Generator.emptyLists
      else {
        for {
          head <- Generator.integersBetween(0, Byte.MaxValue)
          tail <- loop(size - 1)
        } yield head.toByte :: tail
      }
    }
    loop(size)
  }

  def sequence[A, M[X] <: TraversableOnce[X]](in: M[Generator[A]])(
      implicit cbf: CanBuildFrom[M[Generator[A]], A, M[A]]): Generator[M[A]] = {
    in.foldLeft(Generator.single(cbf(in))) { (fr, fa) =>
      for (r <- fr; a <- fa) yield r += a
    } map (_.result())
  }
}
