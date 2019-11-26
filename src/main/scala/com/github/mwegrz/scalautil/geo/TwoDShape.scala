package com.github.mwegrz.scalautil.geo

import com.github.mwegrz.scalautil.quantities.Meters

object TwoDShape {
  val Scale = 1000000
}

sealed trait TwoDShape {
  def contains(position: TwoDPosition): Boolean
}

final case class Polygon(positions: Seq[TwoDPosition]) extends TwoDShape {
  import TwoDShape._

  private lazy val underlaying = {
    val xpoints = positions.map(_.long.value * Scale).map(_.toInt).toArray
    val ypoints = positions.map(_.lat.value * Scale).map(_.toInt).toArray
    new java.awt.Polygon(xpoints, ypoints, positions.size)
  }

  override def contains(position: TwoDPosition): Boolean =
    underlaying.contains(position.long.value * Scale, position.lat.value * Scale)
}

final case class Ellipse(leftUpperCorner: TwoDPosition, latDistance: Meters, longDistance: Meters) extends TwoDShape {
  import TwoDShape._

  private lazy val underlaying = {
    new java.awt.geom.Ellipse2D.Double(
      leftUpperCorner.long.value,
      -leftUpperCorner.lat.value,
      longDistance.value,
      latDistance.value
    )
  }

  override def contains(position: TwoDPosition): Boolean =
    underlaying.contains(position.long.value, -position.lat.value)
}

final case class Circle(center: TwoDPosition, radius: Meters) extends TwoDShape {
  override def contains(position: TwoDPosition): Boolean = center.distanceTo(position) < radius.value
}
