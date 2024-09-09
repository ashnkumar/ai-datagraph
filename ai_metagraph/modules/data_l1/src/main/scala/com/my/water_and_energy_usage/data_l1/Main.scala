package com.my.ai_metagraph.data_l1

import cats.data.NonEmptyList
// import cats.syntax.applicative._ // For .pure
// import cats.syntax.validated._  // For .validNec
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.my.ai_metagraph.shared_data.LifecycleSharedFunctions
import com.my.ai_metagraph.shared_data.calculated_state.CalculatedStateService
import com.my.ai_metagraph.shared_data.deserializers.Deserializers
import com.my.ai_metagraph.shared_data.serializers.Serializers
import com.my.ai_metagraph.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import org.http4s.{EntityDecoder, _}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import cats.effect.Async
import io.circe.syntax._
import io.circe.syntax._
import io.circe.generic.auto._
import com.my.ai_metagraph.shared_data.errors.Errors.valid

import java.util.UUID
import scala.util.Random

object Main extends CurrencyL1App(
  "currency-data_l1",
  "currency data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {

  // Placeholder for faking Firestore data storage
  private def fakeFirestoreSave(data: DatapointUpdate): IO[String] = {
    IO {
      val firestoreData = data.asJson.noSpaces
      println(s"Saving to Firestore: $firestoreData")
      Random.alphanumeric.take(64).mkString // Return a fake hash of the data
    }
  }

  private def makeBaseDataApplicationL1Service(
    calculatedStateService: CalculatedStateService[IO]
  )(implicit F: Async[IO]): BaseDataApplicationL1Service[IO] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[IO, DatapointUpdate, DatapointUpdateState, DatapointUpdateCalculatedState] {
      
      override def validateUpdate(
        update: DatapointUpdate
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = {
        LifecycleSharedFunctions.validateUpdate[IO](update) // Call the enhanced validation
      }

      override def validateData(
        state  : DataState[UsageUpdateState, UsageUpdateCalculatedState],
        updates: NonEmptyList[Signed[UsageUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
        ().validNec.pure[IO]

      override def combine(
        state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
        updates: List[Signed[DatapointUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] =
        state.pure[IO]

      override def routes(implicit context: L1NodeContext[IO]): HttpRoutes[IO] = HttpRoutes.empty

      override def dataEncoder: Encoder[DatapointUpdate] = implicitly[Encoder[DatapointUpdate]]
      override def dataDecoder: Decoder[DatapointUpdate] = implicitly[Decoder[DatapointUpdate]]
      override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] = implicitly[Encoder[DatapointUpdateCalculatedState]]
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

      override def getCalculatedState(implicit context: L1NodeContext[IO]): IO[(SnapshotOrdinal, DatapointUpdateCalculatedState)] =
        calculatedStateService.getCalculatedState.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

      override def setCalculatedState(
        ordinal: SnapshotOrdinal,
        state  : DatapointUpdateCalculatedState
      )(implicit context: L1NodeContext[IO]): IO[Boolean] =
        calculatedStateService.setCalculatedState(ordinal, state)

      override def hashCalculatedState(
        state: DatapointUpdateCalculatedState
      )(implicit context: L1NodeContext[IO]): IO[Hash] =
        calculatedStateService.hashCalculatedState(state)

      override def serializeCalculatedState(
        state: DatapointUpdateCalculatedState
      ): IO[Array[Byte]] = IO(Serializers.serializeCalculatedState(state))

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): IO[Either[Throwable, DatapointUpdateCalculatedState]] = IO(Deserializers.deserializeCalculatedState(bytes))
    }
  )

  private def makeL1Service(implicit F: Async[IO]): IO[BaseDataApplicationL1Service[IO]] = {
    for {
      calculatedStateService <- CalculatedStateService.make[IO]
      dataApplicationL1Service = makeBaseDataApplicationL1Service(calculatedStateService)
    } yield dataApplicationL1Service
  }

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] =
    makeL1Service.asResource.some
}
