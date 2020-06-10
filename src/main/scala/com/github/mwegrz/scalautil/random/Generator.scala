package com.github.mwegrz.scalautil.random

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.TreeMap
import scala.language.higherKinds
import scala.util.Random

object Generator {
  def integers(implicit random: Random): Generator[Int] =
    new Generator[Int] {
      override def generate(): Int = random.nextInt()

      override def generateGaussian(mean: Double, standardDeviation: Double): Int =
        (random.nextGaussian() * standardDeviation + mean).round.toInt
    }

  def longs(implicit random: Random): Generator[Long] =
    new Generator[Long] {
      override def generate(): Long = random.nextLong()

      override def generateGaussian(mean: Double, standardDeviation: Double): Long =
        (random.nextGaussian() * standardDeviation + mean).round
    }

  def doubles(implicit random: Random): Generator[Double] =
    new Generator[Double] {
      override def generate(): Double = random.nextDouble()

      override def generateGaussian(mean: Double, standardDeviation: Double): Double =
        random.nextGaussian() * standardDeviation + mean
    }

  def single[T](x: T): Generator[T] =
    new Generator[T] {
      override def generate(): T = x

      override def generateGaussian(mean: Double, standardDeviation: Double): T = generate()
    }

  def longsBetween(lowest: Long, highest: Long)(implicit random: Random): Generator[Long] =
    for (x <- longs) yield lowest + Math.abs(x) % (highest - lowest)

  def integersBetween(lowest: Int, highest: Int)(implicit random: Random): Generator[Int] =
    for (x <- integers) yield lowest + Math.abs(x) % (highest - lowest)

  def doublesBetween(lowest: Double, highest: Double)(implicit random: Random): Generator[Double] =
    for (x <- doubles) yield lowest + (highest - lowest) * x

  def booleans(implicit random: Random): Generator[Boolean] =
    for (x <- integers) yield x > 0

  def pairs[T, U](t: Generator[T], u: Generator[U])(implicit random: Random): Generator[(T, U)] =
    for {
      x <- t
      y <- u
    } yield (x, y)

  def oneOf[T](xs: T*)(implicit random: Random): Generator[T] = {
    assert(xs.nonEmpty)
    for (idx <- integersBetween(0, xs.length)) yield xs(idx)
  }

  def frequencyOneOf[T](xs: (T, Double)*)(implicit random: Random): Generator[T] =
    new Generator[T] {
      override def generate(): T = {
        var total = 0.toDouble
        val tree: TreeMap[Double, T] = {
          val builder = TreeMap.newBuilder[Double, T]
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

      override def generateGaussian(mean: Double, standardDeviation: Double): T =
        throw new UnsupportedOperationException
    }

  def sequence[A, M[X] <: TraversableOnce[X]](
      in: M[Generator[A]]
  )(implicit cbf: CanBuildFrom[M[Generator[A]], A, M[A]]): Generator[M[A]] = {
    in.foldLeft(Generator.single(cbf(in))) { (fr, fa) =>
      for (r <- fr; a <- fa) yield r += a
    } map (_.result())
  }
}

trait Generator[+T] { self =>
  def generate(): T

  def generateGaussian(mean: Double, standardDeviation: Double): T =
    throw new UnsupportedOperationException

  def map[S](f: T => S): Generator[S] =
    new Generator[S] {
      override def generate(): S = f(self.generate())

      override def generateGaussian(mean: Double, standardDeviation: Double): S =
        f(self.generateGaussian(mean, standardDeviation))
    }

  def flatMap[S](f: T => Generator[S]): Generator[S] =
    new Generator[S] {
      override def generate(): S = f(self.generate()).generate()

      override def generateGaussian(mean: Double, standardDeviation: Double): S =
        f(self.generateGaussian(mean, standardDeviation)).generateGaussian(mean, standardDeviation)
    }
}
