package com.github.mwegrz.scalautil.geo

import com.github.mwegrz.scalautil.quantities.{ Altitude, Latitude, Longitude }

final case class ThreeDPosition(lat: Latitude, long: Longitude, alt: Altitude)
