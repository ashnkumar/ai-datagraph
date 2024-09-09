package com.my.water_and_energy_usage.l0

import cats.data.NonEmptyList
// import cats.syntax.applicative._ // For .pure
// import cats.syntax.validated._  // For .validNec
import io.circe.syntax._
import io.circe.generic.auto._
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.my.water_and_energy_usage.l0.custom_routes.CustomRoutes
import com.my.water_and_energy_usage.shared_data.LifecycleSharedFunctions
import com.my.water_and_energy_usage.shared_data.calculated_state.CalculatedStateService
import com.my.water_and_energy_usage.shared_data.deserializers.Deserializers
import com.my.water_and_energy_usage.shared_data.serializers.Serializers
import com.my.water_and_energy_usage.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l0.CurrencyL0App
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import cats.effect.Async
import com.my.water_and_energy_usage.shared_data.errors.Errors.valid
import java.util.UUID

object Main
  extends CurrencyL0App(
    "currency-l0",
    "currency L0 node",
    ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
    metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
    tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
  ) {

  // Simulating the Firestore logic for storing private data and returning the hash
  private def fakeFirestoreSave(data: DatapointUpdate): IO[String] = {
    IO {
      val firestoreData = data.asJson.noSpaces
      println(s"Storing to Firestore: $firestoreData")
      scala.util.Random.alphanumeric.take(64).mkString // Return a fake hash for storage
    }
  }

  private def makeBaseDataApplicationL0Service(
    calculatedStateService: CalculatedStateService[IO]
  )(implicit F: Async[IO]): BaseDataApplicationL0Service[IO] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[IO, DatapointUpdate, DatapointUpdateState, DatapointUpdateCalculatedState] {

        override def genesis: DataState[DatapointUpdateState, DatapointUpdateCalculatedState] =
          DataState(DatapointUpdateState(List.empty), DatapointUpdateCalculatedState(Map.empty))

        override def validateUpdate(
          update: UsageUpdate
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[IO]

        override def validateData(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: NonEmptyList[Signed[DatapointUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = {
          implicit val sp: SecurityProvider[IO] = context.securityProvider
          LifecycleSharedFunctions.validateData(state, updates)
        }       

        override def combine(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: List[Signed[DatapointUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] = 
          LifecycleSharedFunctions.combine[IO](state, updates)

        override def dataEncoder: Encoder[DatapointUpdate] = implicitly[Encoder[DatapointUpdate]]
        override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] = implicitly[Encoder[DatapointUpdateCalculatedState]]
        override def dataDecoder: Decoder[DatapointUpdate] = implicitly[Decoder[DatapointUpdate]]
        override def calculatedStateDecoder: Decoder[DatapointUpdateCalculatedState] = implicitly[Decoder[DatapointUpdateCalculatedState]]

        override def signedDataEntityDecoder: EntityDecoder[IO, Signed[DatapointUpdate]] = circeEntityDecoder

        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): IO[Array[Byte]] = IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

        override def deserializeBlock(
          bytes: Array[Byte]
        ): IO[Either[Throwable, Signed[DataApplicationBlock]]] = IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

        override def serializeState(
          state: DatapointUpdateState
        ): IO[Array[Byte]] = IO(Serializers.serializeState(state))

        override def deserializeState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateState]] = IO(Deserializers.deserializeState(bytes))

        override def serializeUpdate(
          update: DatapointUpdate
        ): IO[Array[Byte]] = IO(Serializers.serializeUpdate(update))

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdate]] = IO(Deserializers.deserializeUpdate(bytes))

        override def getCalculatedState(implicit context: L0NodeContext[IO]): IO[(SnapshotOrdinal, DatapointUpdateCalculatedState)] =
          calculatedStateService.getCalculatedState.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

        override def setCalculatedState(
          ordinal: SnapshotOrdinal,
          state  : DatapointUpdateCalculatedState
        )(implicit context: L0NodeContext[IO]): IO[Boolean] =
          calculatedStateService.setCalculatedState(ordinal, state)

        override def hashCalculatedState(
          state: DatapointUpdateCalculatedState
        )(implicit context: L0NodeContext[IO]): IO[Hash] =
          calculatedStateService.hashCalculatedState(state)

        override def routes(implicit context: L0NodeContext[IO]): HttpRoutes[IO] =
          CustomRoutes[IO](calculatedStateService, context).public

        override def serializeCalculatedState(
          state: DatapointUpdateCalculatedState
        ): IO[Array[Byte]] = IO(Serializers.serializeCalculatedState(state))

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateCalculatedState]] = IO(Deserializers.deserializeCalculatedState(bytes))
      })

  private def makeL0Service(implicit F: Async[IO]): IO[BaseDataApplicationL0Service[IO]] = {
    for {
      calculatedStateService <- CalculatedStateService.make[IO]
      dataApplicationL0Service = makeBaseDataApplicationL0Service(calculatedStateService)
    } yield dataApplicationL0Service
  }

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] =
    makeL0Service.asResource.some

  override def rewards(implicit sp: SecurityProvider[IO]): Option[Rewards[IO, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent]] =
    None
}
