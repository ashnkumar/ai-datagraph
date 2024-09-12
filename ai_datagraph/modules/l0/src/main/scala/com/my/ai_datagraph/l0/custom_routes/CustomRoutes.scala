package com.my.ai_datagraph.l0.custom_routes

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.my.ai_datagraph.shared_data.calculated_state.CalculatedStateService
import com.my.ai_datagraph.shared_data.types.Types._
import eu.timepit.refined.auto._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._

case class CustomRoutes[F[_]: Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {

  // Existing route to get all data updates
  private def getAllDataUpdates: F[Response[F]] = {
    println("Starting getAllDataUpdates...")

    calculatedStateService.get.flatMap { calculatedState =>
      val onChainUpdates = calculatedState.state.updates

      val dataUpdates = onChainUpdates.values.map { datapointUpdate =>
        datapointUpdate
      }.toList

      Ok(dataUpdates)
    }
  }

  // New route to get data updates filtered by requested shared fields
  private def getDataUpdatesByFields(requestedFields: List[String]): F[Response[F]] = {
    println(s"Starting getDataUpdatesByFields with requested fields: $requestedFields")

    calculatedStateService.get.flatMap { calculatedState =>
      val onChainUpdates = calculatedState.state.updates

      // Filter updates where the datapoint has at least all the requested fields
      val filteredUpdates = onChainUpdates.values.flatMap { datapointUpdate =>
        val matchingFields = datapointUpdate.datapoint.sharedFields.intersect(requestedFields)
        
        // If all requested fields are present, return only those fields
        if (matchingFields.size == requestedFields.size) {
          val filteredData = datapointUpdate.datapoint.copy(sharedFields = matchingFields)
          Some(DatapointUpdate(datapointUpdate.address, filteredData))
        } else {
          None
        }
      }.toList

      Ok(filteredUpdates.asJson)
    }
  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "data-updates" / "all" => getAllDataUpdates

    // Route to fetch data updates that have at least the requested fields
    case req @ GET -> Root / "data-updates" / "by-fields" =>
      req.decode[Json] { json =>
        val requestedFields = json.hcursor.get[List[String]]("fields").getOrElse(Nil)
        getDataUpdatesByFields(requestedFields)
      }
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}
