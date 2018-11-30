package com.rasterfoundry.backsplash.io

import com.rasterfoundry.database.SceneToProjectDao
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.SceneType

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import doobie.implicits._
import fs2.Stream
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram._
import geotrellis.vector.{Projected, Polygon}

import java.util.UUID

object Histogram {

  implicit val xa = RFTransactor.xa

  def getRGBProjectHistogram(projectId: UUID,
                             polygonOption: Option[Projected[Polygon]],
                             redBand: Option[Int] = None,
                             greenBand: Option[Int] = None,
                             blueBand: Option[Int] = None,
                             scenesSubset: List[UUID] = List.empty)(
      implicit cs: ContextShift[IO]): IO[Vector[Histogram[Int]]] = ???
}
