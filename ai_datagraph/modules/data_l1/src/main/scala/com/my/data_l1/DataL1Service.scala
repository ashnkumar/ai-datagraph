package com.my.data_l1

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._

import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.{Hasher, SecurityProvider}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import com.my.shared_data.app.ApplicationConfigOps
import com.my.shared_data.lib.CirceOps.implicits._
import com.my.shared_data.schema.Updates.DataUpdateRaw
import com.my.shared_data.schema.{CalculatedState, OnChainState}
import com.my.shared_data.ValidatorRules

import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder

object DataL1Service {

  def make[F[+_]: Async: JsonSerializer: Hasher: SecurityProvider]: F[BaseDataApplicationL1Service[F]] =
    for {
      validator <- Async[F].pure(DataL1Validator.make[F])
      dataApplicationL1Service = makeBaseApplicationL1Service(validator)
    } yield dataApplicationL1Service

  private def makeBaseApplicationL1Service[F[+_]: Async: JsonSerializer: Hasher: SecurityProvider](
    validator: DataL1Validator[F]
  ): BaseDataApplicationL1Service[F] =
    BaseDataApplicationL1Service[F, DataUpdateRaw, OnChainState, CalculatedState](
      new DataApplicationL1Service[F, DataUpdateRaw, OnChainState, CalculatedState] {

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
          context: L1NodeContext[F]
        ): F[(SnapshotOrdinal, CalculatedState)] =
          (SnapshotOrdinal.MinValue, CalculatedState.genesis).pure[F]

        override def setCalculatedState(ordinal: SnapshotOrdinal, state: CalculatedState)(implicit
          context: L1NodeContext[F]
        ): F[Boolean] = true.pure[F]

        override def hashCalculatedState(state: CalculatedState)(implicit context: L1NodeContext[F]): F[Hash] =
          Hasher[F].hash(state)

        override def validateData(
          state: DataState[OnChainState, CalculatedState],
          updates: NonEmptyList[Signed[DataUpdateRaw]]
        )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          ().validNec[DataApplicationValidationError].pure[F]

        override def validateUpdate(
          update: DataUpdateRaw
        )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          validator.validateUpdate(update)

        override def combine(
          state: DataState[OnChainState, CalculatedState],
          updates: List[Signed[DataUpdateRaw]]
        )(implicit context: L1NodeContext[F]): F[DataState[OnChainState, CalculatedState]] =
          state.pure[F]

        override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] =
          HttpRoutes.empty // No custom routes at L1
      }
    )
}
