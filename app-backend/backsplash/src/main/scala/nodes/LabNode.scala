package com.azavea.rf.backsplash.nodes

import java.util.UUID

import cats.data.{NonEmptyList => NEL}
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.backsplash.io.Mosaic
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.datamodel.{SingleBandOptions, User}
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.database.ToolRunDao
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.reification._
import geotrellis.server.core.maml.metadata._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.spark.{io => _}
import geotrellis.proj4.CRS
import io.circe.generic.semiauto._
import geotrellis.vector._

case class LabNode(
    projectId: UUID,
    redBandOverride: Option[Int] = None,
    greenBandOverride: Option[Int] = None,
    blueBandOverride: Option[Int] = None,
    isSingleBand: Boolean = false,
    singleBandOptions: Option[SingleBandOptions.Params] = None,
    rawSingleBandValues: Boolean = true
) {
  def getBandOverrides: Option[(Int, Int, Int)] =
    (redBandOverride, greenBandOverride, blueBandOverride).tupled
  def toProjectNode: ProjectNode =
    ProjectNode.apply(projectId,
                      redBandOverride,
                      greenBandOverride,
                      blueBandOverride,
                      isSingleBand,
                      singleBandOptions,
                      rawSingleBandValues)
}

object LabNode extends RollbarNotifier with HistogramJsonFormats {

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val labNodeDecoder = deriveDecoder[LabNode]
  implicit val labNodeEncoder = deriveEncoder[LabNode]

  def getClosest(cellWidth: Double, listNums: List[LayoutDefinition]) =
    listNums match {
      case Nil  => Double.MaxValue
      case list => list.minBy(ld => math.abs(ld.cellSize.width - cellWidth))
    }

  implicit val labNodeTmsReification: MamlTmsReification[LabNode] =
    new MamlTmsReification[LabNode] {
      def kind(self: LabNode): MamlKind = MamlKind.Tile

      def tmsReification(self: LabNode, buffer: Int)(
          implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mdIO =
            Mosaic.getMosaicDefinitions(self.toProjectNode, Some(extent))
          for {
            mds <- mdIO
            mbTiles <- Mosaic.getMosaicDefinitionTiles(self.toProjectNode,
                                                       z,
                                                       x,
                                                       y,
                                                       extent,
                                                       mds)
          } yield {
            RasterLit(
              mbTiles.flatten match {
                case Nil => {
                  logger.info(s"NO DATA")
                  Raster(IntArrayTile.fill(NODATA, 256, 256), extent)
                }
                case tiles @ (h :: _) =>
                  tiles reduce {
                    _ merge _
                  }
              }
            )
          }
        }
    }

  implicit val labNodeExtentReification: MamlExtentReification[LabNode] =
    new MamlExtentReification[LabNode] {
      def kind(self: LabNode): MamlKind = MamlKind.Tile
      def extentReification(self: LabNode)(
          implicit t: Timer[IO]): (Extent, CellSize) => IO[Literal] =
        (extent: Extent, cs: CellSize) => {
          val mdIO =
            Mosaic.getMosaicDefinitions(self.toProjectNode, Some(extent))
          for {
            mds <- mdIO
            tiff <- CogUtils.getTiff(mds.head.ingestLocation.toString) // TODO use mosaic def here
          } yield {
            val b = CogUtils.cropGeoTiffToTile(tiff, extent, cs, 0)
            RasterLit(Raster(b, extent))
          }
        }
    }

  implicit val labNodeHasRasterExtents: HasRasterExtents[LabNode] =
    new HasRasterExtents[LabNode] {
      def rasterExtents(self: LabNode)(
          implicit t: Timer[IO]): IO[NEL[RasterExtent]] = {
        val mdIO = Mosaic.getMosaicDefinitions(self.toProjectNode)
        for {
          mds <- mdIO
          tiff <- CogUtils.getTiff(mds.head.ingestLocation.toString)
        } yield {
          NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
        }
      }

      def crs(self: LabNode)(implicit contextShift: Timer[IO]): IO[CRS] = {
        val mdIO = Mosaic.getMosaicDefinitions(self.toProjectNode)
        for {
          mds <- mdIO
          tiff <- CogUtils.getTiff(mds.head.ingestLocation.toString)
        } yield {
          tiff.crs
        }
      }
    }
}
