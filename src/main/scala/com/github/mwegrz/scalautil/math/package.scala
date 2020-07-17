package com.github.mwegrz.scalautil

package object math {
  def round(value: Double, decimalPlaces: Int): Double =
    round(BigDecimal(value), decimalPlaces)

  def round(value: BigDecimal, decimalPlaces: Int): Double =
    value.setScale(decimalPlaces, BigDecimal.RoundingMode.HALF_UP).toDouble

  def normalize(value: Double, min: Double, max: Double): Double = {
    require(max > min, "max must be greater than min")
    require(value <= max && value >= min, "value must be between min and max")

    (value - min) / (max - min)
  }
  def denormalize(value: Double, min: Double, max: Double): Double = {
    require(max > min, "max must be greater than min")
    require(value >= 0 && value <= 1, "value must be between 0 and 1")

    (value * (max - min) + min)
  }
}
