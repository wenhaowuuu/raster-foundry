package com.rasterfoundry

import com.rasterfoundry.backsplash.maml.BacksplashMamlAdapter
import com.rasterfoundry.tool.ast._

import com.rasterfoundry.tool._

import geotrellis.raster.Tile
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.histogram.Histogram

import scala.math.abs
import java.util.Arrays.binarySearch

package object backsplash {

  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends BacksplashMamlAdapter

}
