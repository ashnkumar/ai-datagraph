package com.my.ai_datagraph.l0

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.LifecycleSharedFunctions
import com.my.ai_datagraph.shared_data.calculated_state.CalculatedStateService
import com.my.ai_datagraph.shared_data.postgres.PostgresService
import com.my.ai_datagraph.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

object ML0Service {

  def make[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F],
    postgresService: PostgresService[F]  // Add PostgresService as a dependency
  ): F[BaseDataApplicationL0Service[F]] = Async[F].delay {
    makeBaseDataApplicationL0Service(
      calculatedStateService,
      postgresService  // Pass it here
    )
  }

  private def makeBaseDataApplicationL0Service[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F],
    postgresService: PostgresService[F]  // Pass it here as well
  ): BaseDataApplicationL0Service[F] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[F, DatapointTransaction, DatapointUpdateState, DatapointUpdateCalculatedState] {

        override def genesis: DataState[DatapointUpdateState, DatapointUpdateCalculatedState] = {
          DataState(
            DatapointUpdateState(List.empty),
            DatapointUpdateCalculatedState(Map.empty)
          )
        }

        override def validateUpdate(
          update: DataUpdateRaw
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[F]

        override def validateData(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: NonEmptyList[Signed[DataUpdateRaw]]
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
          // Call the LifecycleSharedFunctions.validateData, passing the state and updates
          LifecycleSharedFunctions.validateData(state, updates)
        }

        override def combine(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: List[Signed[DataUpdateRaw]]
        )(implicit context: L0NodeContext[F]): F[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] =
          LifecycleSharedFunctions.combine[F](state, updates, postgresService)  // Pass the service here

        override def dataEncoder: Encoder[DatapointTransaction] =
          implicitly[Encoder[DatapointTransaction]]

        override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] =
          implicitly[Encoder[DatapointUpdateCalculatedState]]

        override def dataDecoder: Decoder[DatapointTransaction] =
          implicitly[Decoder[DatapointTransaction]]

        override def calculatedStateDecoder: Decoder[DatapointUpdateCalculatedState] =
          implicitly[Decoder[DatapointUpdateCalculatedState]]

        override def signedDataEntityDecoder: EntityDecoder[F, Signed[DatapointTransaction]] =
          circeEntityDecoder

        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[Signed[DataApplicationBlock]](block)

        override def deserializeBlock(
          bytes: Array[Byte]
        ): F[Either[Throwable, Signed[DataApplicationBlock]]] =
          JsonSerializer[F].deserialize[Signed[DataApplicationBlock]](bytes)

        override def serializeState(
          state: DatapointUpdateState
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[DatapointUpdateState](state)

        override def deserializeState(
          bytes: Array[Byte]
        ): F[Either[Throwable, DatapointUpdateState]] =
          JsonSerializer[F].deserialize[DatapointUpdateState](bytes)

        override def serializeUpdate(
          update: DatapointTransaction
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[DatapointTransaction](update)

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): F[Either[Throwable, DataUpdateRaw]] =
          JsonSerializer[F].deserialize[DataUpdateRaw](bytes)

        override def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, DatapointUpdateCalculatedState)] =
          calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

        override def setCalculataedState(
          ordinal: SnapshotOrdinal,
          state  : DatapointUpdateCalculatedState
        )(implicit context: L0NodeContext[F]): F[Boolean] =
          calculatedStateService.set(ordinal, state)

        override def hashCalculatedState(
          state: DatapointUpdateCalculatedState
        )(implicit context: L0NodeContext[F]): F[Hash] =
          calculatedStateService.hash(state)

        override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] =
          HttpRoutes.empty

        override def serializeCalculatedState(
          state: DatapointUpdateCalculatedState
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[DatapointUpdateCalculatedState](state)

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): F[Either[Throwable, DatapointUpdateCalculatedState]] =
          JsonSerializer[F].deserialize[DatapointUpdateCalculatedState](bytes)
      }
    )
}
