package com.azavea.rf.backsplash.services

import cats.effect.Effect
import io.circe.Json
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import com.amazonaws.xray.AWSXRay

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.handlers.TracingHandler;


class HealthCheckService[F[_]: Effect] extends Http4sDsl[F] {
  val service: HttpService[F] = {
    HttpService[F] {
      case GET -> Root => {
        val segment = AWSXRay.beginSegment("Test")
        segment.addSubsegment()
        val subsegment = AWSXRay.beginSubsegment("Starting Healthcheck")
        Thread.sleep(4000)
        subsegment.end()
        Ok(
          Json.obj("message" -> Json.fromString("Healthy"),
                   "reason" -> Json.fromString("A-ok")))
      }
    }
  }
}
