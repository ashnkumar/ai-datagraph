package com.my.metagraph_l0

import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._

import org.tessellation.currency.dataApplication.{DataApplicationValidationError, L0NodeContext}
import org.tessellation.json.JsonSerializer
import org.tessellation.node.shared.ext.http4s.SnapshotOrdinalVar
import org.tessellation.schema.address.{Address, DAGAddressRefined}

import com.my.metagraph_l0.ML0NodeContext.syntax._
import com.my.shared_data.lib.{CheckpointService, MetagraphPublicRoutes}
import com.my.shared_data.schema.{CalculatedState, Updates}
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher

case class FieldFilterRequest(fieldsToInclude: List[String]) // request body for filtering

class ML0CustomRoutes[F[_]: Async: JsonSerializer](calculatedStateService: CheckpointService[F, CalculatedState])(
  implicit context: L0NodeContext[F]
) extends MetagraphPublicRoutes[F] {

  // Custom QueryParamDecoder for List[String]
  implicit val csvListQueryParamDecoder: QueryParamDecoder[List[String]] = 
    QueryParamDecoder[String].map(_.split(",").toList)

  // QueryParamMatcher for 'fields'
  object FieldArrayQueryParamMatcher extends QueryParamDecoderMatcher[List[String]]("fields")

  protected val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    // New route for filtering data updates based on fields
    case GET -> Root / "dataupdates" / "filter" :? FieldArrayQueryParamMatcher(fieldsToInclude) =>
      context.getOnChainState.map(_.map { onChainState =>
        val updates = onChainState.dataUpdates.toList
        // Filter updates where "fields" contain all the provided elements
        val filteredUpdates = updates.filter { case (_, dataUpdate) =>
          fieldsToInclude.forall(field => dataUpdate.fields.contains(field))
        }

        // Extract the privateDataHash values from filtered updates
        val privateDataHashes = filteredUpdates.map(_._2.privateDataHash)

        privateDataHashes
      }).flatMap(prepareResponse(_))

    // Existing routes
    case GET -> Root / "dataupdates" =>
      context.getOnChainState.map(_.map(_.dataUpdates.toList)).flatMap(prepareResponse(_))

    case GET -> Root / "snapshot" / "currency" / "latest" =>
      context.getLatestCurrencySnapshot.flatMap(prepareResponse(_))

    case GET -> Root / "snapshot" / "currency" / SnapshotOrdinalVar(ordinal) =>
      context.getCurrencySnapshotAt(ordinal).flatMap(prepareResponse(_))

    case GET -> Root / "snapshot" / "currency" / SnapshotOrdinalVar(ordinal) / "count-updates" =>
      context.countUpdatesInSnapshotAt(ordinal).flatMap(prepareResponse(_))
  }
}
