package com.rasterfoundry.backsplash.io

import cats.effect.{IO, Timer}
import cats.data._
import cats.implicits._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.nodes.ProjectNode
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.SceneToProjectDao
import com.rasterfoundry.datamodel.{
  MosaicDefinition,
  SceneType,
  SingleBandOptions
}
import doobie.implicits._
import fs2.Stream
import geotrellis.contrib.vlm.{RasterSource, TargetRegion}
import geotrellis.proj4.WebMercator
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.spark.tiling.LayoutLevel
import geotrellis.spark.{SpatialKey, io => _}
import geotrellis.vector.{Extent, Projected}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

object Mosaic extends RollbarNotifier {

  var rasterSourceCache: mutable.Map[UUID, RasterSource] =
    mutable.Map.empty

  import com.rasterfoundry.database.util.RFTransactor.xa

  implicit val timer: Timer[IO] = IO.timer(global)

  def getMosaicDefinitions(self: ProjectNode,
                           extent: Extent): Stream[IO, MosaicDefinition] = {
    self.getBandOverrides match {
      case Some((red, green, blue)) =>
        SceneToProjectDao
          .getMosaicDefinition(
            self.projectId,
            Some(Projected(extent, 3857)),
            Some(red),
            Some(green),
            Some(blue)
          )
          .transact(xa)
      case None =>
        SceneToProjectDao
          .getMosaicDefinition(
            self.projectId,
            Some(Projected(extent, 3857))
          )
          .transact(xa)
    }
  }

  def getMultiBandTileFromMosaic(z: Int, x: Int, y: Int)(
      md: MosaicDefinition): IO[Option[MultibandTile]] = IO {
    val rasterSource = Mosaic.synchronized {
      rasterSourceCache.getOrElseUpdate(
        md.sceneId,
        (md.ingestLocation.map {
          MosaicDefinitionRasterSource.getRasterSource(_)
        } getOrElse {
          throw UningestedScenesException(
            s"Scene ${md.sceneId} has no ingest location")
        }).reproject(WebMercator)
      )
    }
    val extent = MosaicDefinitionRasterSource
      .tmsLevels(z)
      .mapTransform
      .keyToExtent(SpatialKey(x, y))
    logger.debug(s"Got extent: $extent")
    val bands = Seq(md.colorCorrections.redBand,
                    md.colorCorrections.greenBand,
                    md.colorCorrections.blueBand)
    rasterSource.read(extent, bands) map { raster =>
      md.colorCorrections
        .copy(redBand = 0, greenBand = 1, blueBand = 2)
        .colorCorrect(raster.tile, raster.tile.histogramDouble, None)
    }
  }

  def getMosaicDefinitionTile(
      self: ProjectNode,
      z: Int,
      x: Int,
      y: Int,
      extent: Extent,
      md: MosaicDefinition): IO[Option[Raster[MultibandTile]]] =
    if (!self.isSingleBand) {
      getMultiBandTileFromMosaic(z, x, y)(md) map { optionTile =>
        optionTile map { Raster(_, extent) }
      }
    } else {
      logger.info(
        s"Getting Single Band Tile From Mosaic: ${z} ${x} ${y} ${self.projectId}")
      getSingleBandTileFromMosaic(
        extent,
        self.singleBandOptions getOrElse {
          throw SingleBandOptionsException(
            "No single-band options found for single-band visualization")
        },
        self.rawSingleBandValues
      )(md)
    }

  def getSingleBandTileFromMosaic(extent: Extent,
                                  singleBandOptions: SingleBandOptions.Params,
                                  rawSingleBandValues: Boolean)(
      md: MosaicDefinition): IO[Option[Raster[MultibandTile]]] = {
    IO {
      val rasterSource = md.ingestLocation map {
        MosaicDefinitionRasterSource.getRasterSource(_)
      } getOrElse {
        throw UningestedScenesException(
          s"Scene ${md.sceneId} has no ingest location")
      }
      rasterSource.read(extent, Seq(singleBandOptions.band))
    }
  }

  def getMosaicTileForExtent(
      extent: Extent,
      cellSize: CellSize,
      singleBandOptions: Option[SingleBandOptions.Params],
      singleBand: Boolean)(
      md: MosaicDefinition): IO[Option[Raster[MultibandTile]]] = ???
  //   {
  //   md.sceneType match {
  //     case Some(SceneType.COG) =>
  //       Cog.tileForExtent(extent, cellSize, singleBandOptions, singleBand, md)
  //     case Some(SceneType.Avro) =>
  //       Avro.tileForExtent(extent, cellSize, singleBandOptions, singleBand, md)
  //     case None =>
  //       throw UnknownSceneTypeException(
  //         "Unable to fetch tiles with unknown scene type")
  //   }
  // }

  @inline def maybeResample(
      tile: Raster[MultibandTile]): Raster[MultibandTile] =
    if (tile.dimensions != (256, 256)) tile.resample(256, 256) else tile
}
