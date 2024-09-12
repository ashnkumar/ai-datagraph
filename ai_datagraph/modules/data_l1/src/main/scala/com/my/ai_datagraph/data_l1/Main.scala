package com.my.ai_datagraph.data_l1

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.calculated_state.CalculatedStateService
import com.my.ai_datagraph.shared_data.types.Types._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.http4s.{EntityDecoder, _}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.tessellation.BuildInfo
import com.my.ai_datagraph.shared_data.LifecycleSharedFunctions
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets
import java.util.UUID

object Main extends CurrencyL1App(
  "currency-data_l1",
  "currency data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {

  private def serialize[A: Encoder](serializableData: A): Array[Byte] = {
    serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  private def deserialize[A: Decoder](bytes: Array[Byte]): Either[Throwable, A] = {
    io.circe.jawn.decode[A](new String(bytes, StandardCharsets.UTF_8))
  }

  private def makeBaseDataApplicationL1Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL1Service[IO] = {
    BaseDataApplicationL1Service(
      new DataApplicationL1Service[IO, DataUpdateRaw, DatapointUpdateState, DatapointUpdateCalculatedState] {

        override def validateUpdate(
          update: DataUpdateRaw
        )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = {
          LifecycleSharedFunctions.validateUpdate[IO](update)
        }

        override def validateData(
          state: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: NonEmptyList[Signed[DataUpdateRaw]]
        )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = {
          println("L1 validateData called")
          ().validNec.pure[IO]
        }

        // The combine function here is a no-op in data_l1
        override def combine(
          state: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: List[Signed[DataUpdateRaw]]
        )(implicit context: L1NodeContext[IO]): IO[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] = {
          println("L1 combine called")
          state.pure[IO] // Do nothing in data_l1
        }

        override def routes(implicit context: L1NodeContext[IO]): HttpRoutes[IO] = {
          println("L1 routes called")
          HttpRoutes.empty
        }

        override def dataEncoder: Encoder[DataUpdateRaw] = implicitly[Encoder[DataUpdateRaw]]
        override def dataDecoder: Decoder[DataUpdateRaw] = implicitly[Decoder[DataUpdateRaw]]
        override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] = implicitly[Encoder[DatapointUpdateCalculatedState]]
        override def calculatedStateDecoder: Decoder[DatapointUpdateCalculatedState] = implicitly[Decoder[DatapointUpdateCalculatedState]]
        override def signedDataEntityDecoder: EntityDecoder[IO, Signed[DataUpdateRaw]] = circeEntityDecoder

        // Serialization and deserialization for blocks containing DataUpdateRaw
        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): IO[Array[Byte]] = IO {
          serialize[Signed[DataApplicationBlock]](block)
        }

        override def deserializeBlock(
          bytes: Array[Byte]
        ): IO[Either[Throwable, Signed[DataApplicationBlock]]] = IO {
          deserialize[Signed[DataApplicationBlock]](bytes)
        }

        override def serializeState(
          state: DatapointUpdateState
        ): IO[Array[Byte]] = IO {
          serialize[DatapointUpdateState](state)
        }

        override def deserializeState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateState]] = IO {
          deserialize[DatapointUpdateState](bytes)
        }

        override def serializeUpdate(
          update: DataUpdateRaw
        ): IO[Array[Byte]] = IO {
          serialize[DataUpdateRaw](update)
        }

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DataUpdateRaw]] = IO {
          deserialize[DataUpdateRaw](bytes)
        }

        override def getCalculatedState(implicit context: L1NodeContext[IO]): IO[(SnapshotOrdinal, DatapointUpdateCalculatedState)] = {
          println("L1 getCalculatedState called")
          calculatedStateService.getCalculatedState.map(calculatedState => (calculatedState.ordinal, calculatedState.state))
        }

        override def setCalculatedState(
          ordinal: SnapshotOrdinal,
          state: DatapointUpdateCalculatedState
        )(implicit context: L1NodeContext[IO]): IO[Boolean] = {
          println("L1 setCalculatedState called")
          calculatedStateService.setCalculatedState(ordinal, state)
        }

        override def hashCalculatedState(
          state: DatapointUpdateCalculatedState
        )(implicit context: L1NodeContext[IO]): IO[Hash] = {
          println("L1 hashCalculatedState called")
          calculatedStateService.hashCalculatedState(state)
        }

        override def serializeCalculatedState(
          state: DatapointUpdateCalculatedState
        ): IO[Array[Byte]] = IO {
          serialize[DatapointUpdateCalculatedState](state)
        }

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateCalculatedState]] = IO {
          deserialize[DatapointUpdateCalculatedState](bytes)
        }
      }
    )
  }

  private def makeL1Service: IO[BaseDataApplicationL1Service[IO]] = {
    println("L1 makeL1Service called")
    for {
      calculatedStateService <- CalculatedStateService.make[IO]
      dataApplicationL1Service = makeBaseDataApplicationL1Service(calculatedStateService)
    } yield dataApplicationL1Service
  }

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] = {
    println("L1 dataApplication called")
    makeL1Service.asResource.some
  }
}


//  lastTxnOrdinal = SnapshotOrdinal(NonNegLong.unsafeFrom(0L)),
// import eu.timepit.refined.types.numeric.NonNegLong