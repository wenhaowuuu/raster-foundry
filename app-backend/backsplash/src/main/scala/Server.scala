package com.azavea.rf.backsplash

import com.azavea.rf.backsplash.auth.Authenticators
import com.azavea.rf.backsplash.error._
import com.azavea.rf.backsplash.nodes._
import com.azavea.rf.backsplash.services.{HealthCheckService, MosaicService}
import com.azavea.rf.backsplash.analysis.AnalysisService
import doobie.util.analysis.Analysis

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import fs2.StreamApp
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.AutoSlash

import scala.concurrent.ExecutionContext.Implicits.global

import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
import org.http4s.servlet.syntax.ServletContextSyntax

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {
  def healthCheckService = new HealthCheckService[IO].service
  override def contextInitialized(sce: ServletContextEvent) = {
    val context = sce.getServletContext
    context.mountService("/healthcheck", AutoSlash(healthCheckService))
  }
  override def contextDestroyed(sce: ServletContextEvent) = ()
}
