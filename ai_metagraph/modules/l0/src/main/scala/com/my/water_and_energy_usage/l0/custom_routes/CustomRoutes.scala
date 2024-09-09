package com.my.ai_metagraph.l0.custom_routes

import cats.effect.Async
import cats.syntax.all._
import com.my.ai_metagraph.shared_data.calculated_state.CalculatedStateService
import com.my.ai_metagraph.shared_data.types.Types._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec._
import org.http4s.server.middleware.CORS
import org.tessellation.currency.dataApplication.L0NodeContext

case class CustomRoutes[F[_]: Async](
  calculatedStateService: CalculatedStateService[F],
  context: L0NodeContext[F]
) extends Http4sDsl[F] {

  /**
   * Query data updates based on the requested fields
   *
   * This route handles the querying of data updates. The shared fields
   * of each update will be matched against the requested fields in the query.
   * Only the data updates matching all requested fields will be returned.
   */
  private def queryDataUpdates(
    requestedFields: List[String]
  ): F[Response[F]] = {
    calculatedStateService.getCalculatedState.flatMap { calculatedState =>
      // Collect all transactions from all devices
      val allTransactions = calculatedState.state.devices.values.flatMap(_.datapoints).toList

      // Filter transactions based on the requested fields
      val filteredTransactions = allTransactions.filter { transaction =>
        // We check if all requested fields are included in the shared fields of each transaction
        requestedFields.forall(transaction.datapoint.sharedFields.contains)
      }

      // Return the filtered transactions as a JSON response with just the hash values
      val datapointHashes = filteredTransactions.map(_.datapoint.hash).asJson

      Ok(datapointHashes)
    }
  }

  // Define a query parameter matcher for "fields"
  object DataFieldsQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("fields")

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    // Route for querying data updates based on the fields passed in the query parameter
    case GET -> Root / "data" :? DataFieldsQueryParamMatcher(fields) =>
      fields match {
        case Some(fieldsStr) =>
          val requestedFields = fieldsStr.split(",").toList
          queryDataUpdates(requestedFields)
        case None =>
          BadRequest("Missing required query parameter: fields")
      }
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)
}
