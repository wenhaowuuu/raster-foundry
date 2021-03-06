package com.rasterfoundry.database

import com.rasterfoundry.datamodel.FeatureFlag
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._

class FeatureFlagDaoSpec extends FunSuite with Matchers with DBTestConfig {
  test("types") {
    xa.use(t => FeatureFlagDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }
}
