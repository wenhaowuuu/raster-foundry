package com.rasterfoundry.backsplash.io

import com.rasterfoundry.common.RollbarNotifier

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import geotrellis.contrib.vlm.gdal.{GDALBaseRasterSource, GDALRasterSource}
import geotrellis.raster.{CellSize, RasterExtent}
import geotrellis.server.vlm._

import java.net.URLDecoder

object MosaicDefinitionRasterSource
    extends RasterSourceUtils
    with RollbarNotifier {
  def getRasterSource(uri: String): GDALBaseRasterSource =
    GDALRasterSource(
      URLDecoder.decode(
        "s3://test.objects/fdb6e403-0603-4c25-b767-85f189bdbcdd_COG.tif"))

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val rs = getRasterSource(
      URLDecoder.decode("s3://fdb6e403-0603-4c25-b767-85f189bdbcdd_COG.tif"))
    val dataset = rs.dataset
    val band = dataset.GetRasterBand(1)

    NEL(
      rs.rasterExtent,
      (0 until band.GetOverviewCount()).toList.map { idx =>
        val ovr = band.GetOverview(idx)
        RasterExtent(rs.extent, CellSize(ovr.GetXSize(), ovr.GetYSize()))
      }
    )
  }
}
