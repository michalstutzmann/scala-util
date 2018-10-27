package com.github.mwegrz

import java.util.UUID
import java.util.concurrent.TimeUnit

import com.typesafe.config.{ Config, ConfigFactory }
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

package object scalautil {
  implicit def javaDurationToDuration(duration: java.time.Duration): FiniteDuration =
    FiniteDuration(duration.toNanos, TimeUnit.NANOSECONDS)

  implicit class ConfigOps(config: Config) {
    def withReferenceDefaults(path: String): Config = {

      lazy val referenceExists = ConfigFactory.defaultReference.hasPath(path)
      lazy val defaults = ConfigFactory.defaultReference.getConfig(path)

      if (referenceExists) config.withFallback(defaults) else config
    }
  }

  /*def retry[T](f: => Future[T], delay: Seq[FiniteDuration], retries: Int, defaultDelay: FiniteDuration )(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f recoverWith { case _ if retries > 0 => after(delay.headOption.getOrElse(defaultDelay), s)(retry(f, delay.tail, retries - 1 , defaultDelay)) }
  }

  object RetryDelays {
    def withDefault(delays: List[FiniteDuration], retries: Int, default: FiniteDuration) = {
      if (delays.length > retries) delays take retries
      else delays ++ List.fill(retries - delays.length)(default)
    }

    def withJitter(delays: Seq[FiniteDuration], maxJitter: Double, minJitter: Double) =
      delays.map(_ * (minJitter + (maxJitter - minJitter) * Random.nextDouble))

    val fibonacci: Stream[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacci zip fibonacci.tail).map{ t => t._1 + t._2 }
  }*/

}
