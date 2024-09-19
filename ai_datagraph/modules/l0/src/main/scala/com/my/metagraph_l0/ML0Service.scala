package com.my.metagraph_l0

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{
  DataApplicationBlock,
  DataApplicationValidationErrorOr
}
import org.tessellation.currency.schema.currency.CurrencyIncrementalSnapshot
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.{Hashed, Hasher, SecurityProvider}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import com.my.metagraph_l0.ML0NodeContext.syntax.dataStateOps
import com.my.shared_data.lib.CirceOps.implicits._
import com.my.shared_data.lib.syntax.{CurrencyIncrementalSnapshotOps, ListSignedUpdateOps}
import com.my.shared_data.lib.{Checkpoint, CheckpointService, LatestUpdateValidator, PostgresService}
import com.my.shared_data.schema.Updates.DataUpdateRaw
import com.my.shared_data.schema.{CalculatedState, OnChainState}

import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.typelevel.log4cats.Logger

import com.my.shared_data.LifecycleSharedFunctions

object ML0Service {

  def make[F[+_]: Async: SecurityProvider: Hasher: JsonSerializer: Logger](
    postgresService: PostgresService[F]
  ): F[BaseDataApplicationL0Service[F]] = for {
    checkpointService <- CheckpointService.make[F, CalculatedState](CalculatedState.genesis)
    validator = LatestUpdateValidator.make[F, Signed[DataUpdateRaw], DataState[OnChainState, CalculatedState]](
      ML0Validator.make[F]
    )
    dataApplicationL0Service = makeBaseApplicationL0Service(checkpointService, validator, postgresService)
  } yield dataApplicationL0Service

  private def makeBaseApplicationL0Service[F[+_]: Async: SecurityProvider: Hasher: JsonSerializer: Logger](
    checkpointService: CheckpointService[F, CalculatedState],
    validator: LatestUpdateValidator[F, Signed[DataUpdateRaw], DataState[OnChainState, CalculatedState]],
    postgresService: PostgresService[F]
  ): BaseDataApplicationL0Service[F] =
    BaseDataApplicationL0Service[F, DataUpdateRaw, OnChainState, CalculatedState](
      new DataApplicationL0Service[F, DataUpdateRaw, OnChainState, CalculatedState] {

        override def serializeState(state: OnChainState): F[Array[Byte]] =
          JsonSerializer[F].serialize[OnChainState](state)

        override def deserializeState(bytes: Array[Byte]): F[Either[Throwable, OnChainState]] =
          JsonSerializer[F].deserialize[OnChainState](bytes)

        override def serializeUpdate(update: DataUpdateRaw): F[Array[Byte]] =
          JsonSerializer[F].serialize[DataUpdateRaw](update)

        override def deserializeUpdate(bytes: Array[Byte]): F[Either[Throwable, DataUpdateRaw]] =
          JsonSerializer[F].deserialize[DataUpdateRaw](bytes)

        override def serializeBlock(block: Signed[DataApplicationBlock]): F[Array[Byte]] =
          JsonSerializer[F].serialize[Signed[DataApplicationBlock]](block)

        override def deserializeBlock(bytes: Array[Byte]): F[Either[Throwable, Signed[DataApplicationBlock]]] =
          JsonSerializer[F].deserialize[Signed[DataApplicationBlock]](bytes)

        override def serializeCalculatedState(calculatedState: CalculatedState): F[Array[Byte]] =
          JsonSerializer[F].serialize[CalculatedState](calculatedState)

        override def deserializeCalculatedState(bytes: Array[Byte]): F[Either[Throwable, CalculatedState]] =
          JsonSerializer[F].deserialize[CalculatedState](bytes)

        override def dataEncoder: Encoder[DataUpdateRaw] = implicitly[Encoder[DataUpdateRaw]]

        override def dataDecoder: Decoder[DataUpdateRaw] = implicitly[Decoder[DataUpdateRaw]]

        override def calculatedStateEncoder: Encoder[CalculatedState] = implicitly[Encoder[CalculatedState]]

        override def calculatedStateDecoder: Decoder[CalculatedState] = implicitly[Decoder[CalculatedState]]

        override val signedDataEntityDecoder: EntityDecoder[F, Signed[DataUpdateRaw]] = circeEntityDecoder

        override def getCalculatedState(implicit
          context: L0NodeContext[F]
        ): F[(SnapshotOrdinal, CalculatedState)] =
          checkpointService.get.map(checkpoint => (checkpoint.ordinal, checkpoint.state))

        override def setCalculatedState(ordinal: SnapshotOrdinal, state: CalculatedState)(implicit
          context: L0NodeContext[F]
        ): F[Boolean] = checkpointService.set(Checkpoint(ordinal, state))

        override def hashCalculatedState(state: CalculatedState)(implicit context: L0NodeContext[F]): F[Hash] =
          Hasher[F].hash(state)

        override def genesis: DataState[OnChainState, CalculatedState] =
          DataState(OnChainState.genesis, CalculatedState.genesis)

        override def onSnapshotConsensusResult(snapshot: Hashed[CurrencyIncrementalSnapshot])(implicit
          A: Applicative[F]
        ): F[Unit] =
          for {
            _               <- Logger[F].debug("Evaluating onSnapshotConsensusResult")
            numberOfUpdates <- snapshot.signed.value.countUpdates
            _ <- Logger[F].info(
              s"[onSnapshotConsensusResult] Got $numberOfUpdates updates for ordinal: ${snapshot.ordinal.value}"
            )
          } yield ()

        override def validateData(
          state: DataState[OnChainState, CalculatedState],
          updates: NonEmptyList[Signed[DataUpdateRaw]]
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          state.verify(updates)(validator)

        override def validateUpdate(update: DataUpdateRaw)(implicit
          context: L0NodeContext[F]
        ): F[DataApplicationValidationErrorOr[Unit]] = ().validNec.pure[F]

        override def combine(
          state: DataState[OnChainState, CalculatedState],
          updates: List[Signed[DataUpdateRaw]]
        )(implicit context: L0NodeContext[F]): F[DataState[OnChainState, CalculatedState]] =
          LifecycleSharedFunctions.combine[F](
            state,
            updates,
            postgresService
          )

        override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] =
          new ML0CustomRoutes[F](checkpointService).public
      }
    )
}
