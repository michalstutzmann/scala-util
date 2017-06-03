package com.github.mwegrz.scalautil.resource.sync

import scala.language.reflectiveCalls

/** Implementations of the loan pattern: [[https://wiki.scala-lang.org/display/SYGN/Loan]] .
  * Direct ports of the try-with-resources pattern: [[http://docs.oracle.com/javase/specs/jls/se7/html/jls-14.html#jls-14.20.3.1]].
  */
object arm {
  def using[A <: { def close() }, R](res: A)(f: A => R): R = {
    var pt: Throwable = null
    try {
      f(res)
    } catch {
      case t: Throwable =>
        pt = t
        throw t
    } finally {
      if (res != null) {
        if (pt != null) {
          try {
            res.close()
          } catch {
            case t: Throwable => pt.addSuppressed(t)
          }
        } else {
          res.close()
        }
      }
    }
  }

  def using[A <: { def close() }, B <: { def close() }, R](res1: A, res2: B)(f: (A, B) => R): R = {
    var pt: Throwable = null
    try {
      f(res1, res2)
    } catch {
      case t: Throwable =>
        pt = t
        throw t
    } finally {
      if (res1 != null) {
        if (pt != null) {
          try {
            res1.close()
          } catch {
            case t: Throwable => pt.addSuppressed(t)
          }
        } else {
          res1.close()
        }
      }
      if (res2 != null) {
        if (pt != null) {
          try {
            res2.close()
          } catch {
            case t: Throwable => pt.addSuppressed(t)
          }
        } else {
          res2.close()
        }
      }
    }
  }
}
