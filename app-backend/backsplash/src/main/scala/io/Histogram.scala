package com.rasterfoundry.backsplash.io

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.{LayerAttributeDao, SceneToProjectDao}
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel.SceneType
import com.rasterfoundry.tool.ast.codec.MapAlgebraUtilityCodecs

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import doobie.implicits._
import fs2.Stream
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram._
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.spark.LayerId
import geotrellis.vector.{Projected, Polygon}
import io.circe._
import io.circe.parser._
import io.circe.syntax._

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.util.UUID

object Histogram extends HistogramJsonFormats with RollbarNotifier {

  implicit val xa = RFTransactor.xa

  implicit val sprayJsonEncoder: Encoder[JsValue] = new Encoder[JsValue] {
    final def apply(jsvalue: JsValue): Json =
      parse(jsvalue.compactPrint) match {
        case Right(success) => success
        case Left(fail)     => throw fail
      }
  }

  implicit val histogramDecoder: Decoder[Histogram[Double]] =
    Decoder[Json].map { js =>
      js.noSpaces.parseJson.convertTo[Histogram[Double]]
    }
  implicit val histogramEncoder: Encoder[Histogram[Double]] =
    new Encoder[Histogram[Double]] {
      final def apply(hist: Histogram[Double]): Json = hist.toJson.asJson
    }

  def getRGBProjectHistogram(projectId: UUID,
                             polygonOption: Option[Projected[Polygon]],
                             redBand: Option[Int] = None,
                             greenBand: Option[Int] = None,
                             blueBand: Option[Int] = None,
                             scenesSubset: List[UUID] = List.empty)(
      implicit cs: ContextShift[IO]): IO[Vector[Histogram[Int]]] = ???

  def getSingleBandProjectHistogram(projectId: UUID,
                                    polygonOption: Option[Projected[Polygon]],
                                    band: Int,
                                    scenesSubset: List[UUID] = List.empty)(
      implicit cs: ContextShift[IO]): IO[Histogram[Double]] =
    (for {
      mosaicDefinition <- SceneToProjectDao
        .getMosaicDefinition(projectId,
                             polygonOption,
                             None,
                             None,
                             None,
                             scenesSubset)
        .transact(xa)
      _ <- Stream.emit { logger.info("got a mosaic definition") }
      histAttribute <- Stream.eval {
        LayerAttributeDao
          .unsafeGetAttribute(LayerId(mosaicDefinition.sceneId.toString, 0),
                              "histogram")
          .transact(xa)
      }
      _ <- Stream.emit { logger.info("attribute done all set") }
      hist = histAttribute.value.as[Array[Histogram[Double]]]
      _ <- Stream.emit {
        logger
          .info(
            s"Hist min max values: ${hist map { _ map { _.minMaxValues } }}")
      }
    } yield { hist })
      .collect({ case Right(h) => h })
      .compile
      .fold(StreamingHistogram(): Histogram[Double])(
        (hist1: Histogram[Double], hist2: Array[Histogram[Double]]) =>
          hist1 merge hist2(band))
}
