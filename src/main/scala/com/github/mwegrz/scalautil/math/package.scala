package com.github.mwegrz.scalautil

package object math {
  def round(value: Double, decimalPlaces: Int): Double =
    round(BigDecimal(value), decimalPlaces)

  def round(value: BigDecimal, decimalPlaces: Int): Double =
    value.setScale(decimalPlaces, BigDecimal.RoundingMode.HALF_UP).toDouble
}
