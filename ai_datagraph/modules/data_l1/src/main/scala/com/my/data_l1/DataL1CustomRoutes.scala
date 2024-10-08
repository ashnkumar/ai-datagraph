package com.my.data_l1

import cats.effect.{Async, Clock}
import cats.implicits.{toFlatMapOps, toFunctorOps}
import cats.syntax.either._

import org.tessellation.currency.dataApplication.{DataApplicationValidationError, L1NodeContext}
import org.tessellation.json.JsonSerializer

import com.my.data_l1.DataL1NodeContext.syntax._
import com.my.shared_data.lib.MetagraphPublicRoutes

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes

class DataL1CustomRoutes[F[_]: Async: JsonSerializer](implicit context: L1NodeContext[F])
    extends MetagraphPublicRoutes[F] {

  protected val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "current-time" =>
      Clock[F].realTime
        .map { now =>
          ("epochMillis" -> now.toMillis).asJson.asRight[DataApplicationValidationError]
        }
        .flatMap(prepareResponse(_))

    case GET -> Root / "snapshot" / "global" / "latest" =>
      context.getLatestGlobalSnapshot.flatMap(prepareResponse(_))

    case GET -> Root / "snapshot" / "currency" / "latest" =>
      context.getLatestCurrencySnapshot.flatMap(prepareResponse(_))
  }
}