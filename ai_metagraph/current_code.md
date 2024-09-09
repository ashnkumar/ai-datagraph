## Dependencies.scala
**Path:** `./project/Dependencies.scala`

```scala
import sbt.*

object Dependencies {

  object V {
    val tessellation = "2.8.1"
    val decline = "2.4.1"
  }

  def tessellation(artifact: String): ModuleID = "org.constellation" %% s"tessellation-$artifact" % V.tessellation

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  object Libraries {
    val tessellationNodeShared = tessellation("node-shared")
    val tessellationCurrencyL0 = tessellation("currency-l0")
    val tessellationCurrencyL1 = tessellation("currency-l1")
    val declineCore = decline()
    val declineEffect = decline("effect")
    val declineRefined = decline("refined")
    val requests = "com.lihaoyi" %% "requests" % "0.8.0"
  }


  // Scalafix rules
  val organizeImports = "com.github.liancheng" %% "organize-imports" % "0.5.0"

  object CompilerPlugin {

    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % "0.3.1"
    )

    val kindProjector = compilerPlugin(
      ("org.typelevel" % "kind-projector" % "0.13.2").cross(CrossVersion.full)
    )

    val semanticDB = compilerPlugin(
      ("org.scalameta" % "semanticdb-scalac" % "4.7.1").cross(CrossVersion.full)
    )
  }
}

```

## LifecycleSharedFunctions.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/LifecycleSharedFunctions.scala`

```scala
package com.my.water_and_energy_usage.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.Utils.getAllAddressesFromProofs
import com.my.water_and_energy_usage.shared_data.combiners.Combiners.combineDatapointUpdate
import com.my.water_and_energy_usage.shared_data.types.Types.{DatapointUpdate, DatapointUpdateCalculatedState, DatapointUpdateState}
import com.my.water_and_energy_usage.shared_data.validations.Validations.{validateDatapointUpdate, validateDatapointUpdateSigned}
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  private def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  def validateUpdate[F[_] : Async](
    update: DatapointUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    validateDatapointUpdate(update, none)
  }

  def validateData[F[_] : Async : SecurityProvider](
    oldState: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates : NonEmptyList[Signed[DatapointUpdate]]
  ): F[DataApplicationValidationErrorOr[Unit]] =
    updates.traverse { update =>
      getAllAddressesFromProofs(update.proofs)
        .flatMap { addresses =>
          Async[F].delay(validateDatapointUpdateSigned(update, oldState.calculated, addresses))
        }
    }.map(_.reduce)

  def combine[F[_] : Async](
    oldState: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates : List[Signed[DatapointUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] = {
    val newState = DataState(
      DatapointUpdateState(List.empty),
      DatapointUpdateCalculatedState(oldState.calculated.devices)
    )

    val lastSnapshotOrdinal: F[SnapshotOrdinal] = context.getLastCurrencySnapshot.flatMap {
      case Some(value) => value.ordinal.pure[F]
      case None =>
        val message = "Could not get the ordinal from currency snapshot. lastCurrencySnapshot not found"
        logger.error(message) >> new Exception(message).raiseError[F, SnapshotOrdinal]
    }

    if (updates.isEmpty) {
      logger.info("Snapshot without any check-ins, updating the state to empty updates").as(newState)
    } else {
      updates.foldLeftM(newState) { (acc, signedUpdate) =>
        lastSnapshotOrdinal.map(combineDatapointUpdate(signedUpdate, acc, _))
      }
    }
  }
}

```

## Utils.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/Utils.scala`

```scala
package com.my.water_and_energy_usage.shared_data

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.serializers.Serializers
import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdate
import io.circe.Json
import org.tessellation.schema.address.Address
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.signature.SignatureProof

object Utils {

  def getUpdateHash(
    update: DatapointUpdate
  ): String =
    Hash.fromBytes(Serializers.serializeUpdate(update)).value

  def getAllAddressesFromProofs[F[_] : Async : SecurityProvider](
    proofs: NonEmptySet[SignatureProof]
  ): F[List[Address]] =
    proofs
      .map(_.id)
      .toList
      .traverse(_.toAddress[F])

  def removeKeyFromJSON(json: Json, keyToRemove: String): Json =
    json.mapObject { obj =>
      obj.remove(keyToRemove).mapValues {
        case objValue: Json => removeKeyFromJSON(objValue, keyToRemove)
        case other => other
      }
    }.mapArray { arr =>
      arr.map(removeKeyFromJSON(_, keyToRemove))
    }
}

```

## Deserializers.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/deserializers/Deserializers.scala`

```scala
package com.my.water_and_energy_usage.shared_data.deserializers

import com.my.water_and_energy_usage.shared_data.types.Types.{DatapointUpdate, DatapointUpdateCalculatedState, DatapointUpdateState}
import io.circe.Decoder
import io.circe.jawn.decode
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Deserializers {
  private def deserialize[A: Decoder](
    bytes: Array[Byte]
  ): Either[Throwable, A] =
    decode[A](new String(bytes, StandardCharsets.UTF_8))

  def deserializeUpdate(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdate] =
    deserialize[DatapointUpdate](bytes)

  def deserializeState(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdateState] =
    deserialize[DatapointUpdateState](bytes)

  def deserializeBlock(
    bytes: Array[Byte]
  )(implicit e: Decoder[DataUpdate]): Either[Throwable, Signed[DataApplicationBlock]] =
    deserialize[Signed[DataApplicationBlock]](bytes)

  def deserializeCalculatedState(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdateCalculatedState] =
    deserialize[DatapointUpdateCalculatedState](bytes)
}

```

## Types.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/types/Types.scala`

```scala
package com.my.water_and_energy_usage.shared_data.types

import com.my.water_and_energy_usage.shared_data.Utils.removeKeyFromJSON
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.syntax.EncoderOps
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Types {
  val DatapointUpdateType = "DatapointUpdate"

  @derive(decoder, encoder)
  case class Datapoint(
    data        : String,  // The actual data point (e.g., health data)
    timestamp   : Long,    // When the data was recorded
    privacyLevel: String   // The privacy level chosen by the consumer
  )

  // Represents an update for a single datapoint from a specific address
  @derive(decoder, encoder)
  case class DatapointUpdate(
    address     : Address,  // Unique identifier for the consumer device (using Address)
    datapoint   : Datapoint // The datapoint sent in this update
  ) extends DataUpdate

  // Represents a transaction that encapsulates a DatapointUpdate with metadata
  @derive(decoder, encoder)
  case class DatapointTransaction(
    owner           : Address,         // Owner address of the update
    transactionType : String,          // Type of transaction (could be used for metadata if needed)
    datapoint       : Datapoint,       // The actual datapoint
    lastTxnOrdinal  : SnapshotOrdinal, // Snapshot ordinal of the transaction
    lastTxnHash     : String           // Hash of the transaction
  )

  // State representation on-chain, storing all datapoint updates
  @derive(decoder, encoder)
  case class DatapointUpdateState(
    updates: List[Signed[DatapointTransaction]] // List of signed transactions for audit and history
  ) extends DataOnChainState

  @derive(decoder, encoder)
  case class DeviceCalculatedState(
    datapoints   : List[DatapointTransaction],  // Aggregated transactions for the device
    currentTxnRef: TxnRef            // Last transaction reference for this device
  )

  object DeviceCalculatedState {
    def empty: DeviceCalculatedState = DeviceCalculatedState(List.empty, TxnRef.empty)
  }

  @derive(decoder, encoder)
  case class DatapointUpdateCalculatedState(
    devices: Map[Address, DeviceCalculatedState]  // Map of Address to calculated state
  ) extends DataCalculatedState

  object DatapointUpdateCalculatedState {
    def hash(state: DatapointUpdateCalculatedState): Hash =
      Hash.fromBytes(
        removeKeyFromJSON(state.asJson, "timestamp")
          .deepDropNullValues
          .noSpaces
          .getBytes(StandardCharsets.UTF_8)
      )
  }

  @derive(decoder, encoder)
  case class TxnRef(
    txnSnapshotOrdinal: SnapshotOrdinal,
    txnHash           : String
  )

  object TxnRef {
    def empty: TxnRef = TxnRef(SnapshotOrdinal.MinValue, Hash.empty.value)
  }

  @derive(decoder, encoder)
  case class AddressTransactionsWithLastRef(txnRef: TxnRef, txns: List[DatapointTransaction])
}

```

## Combiners.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/combiners/Combiners.scala`

```scala
package com.my.water_and_energy_usage.shared_data.combiners

import com.my.water_and_energy_usage.shared_data.Utils.getUpdateHash
import com.my.water_and_energy_usage.shared_data.types.Types._
import eu.timepit.refined.types.numeric.NonNegLong
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.Signed

object Combiners {
  
  private def getUpdatedDeviceDatapoints(
    datapoint: Datapoint,
    acc      : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    address  : Address
  ): List[DatapointTransaction] = {
    val deviceCalculatedState = acc.calculated.devices.getOrElse(address, DeviceCalculatedState.empty)
    deviceCalculatedState.datapoints :+ DatapointTransaction(address, DatapointUpdateType, datapoint, SnapshotOrdinal.MinValue, "") // Placeholder values
  }

  def combineDatapointUpdate(
      signedUpdate       : Signed[DatapointUpdate],
      acc                : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
      lastSnapshotOrdinal: SnapshotOrdinal
  ): DataState[DatapointUpdateState, DatapointUpdateCalculatedState] = {

    val update = signedUpdate.value
    val address = update.address
    val updateHash = getUpdateHash(signedUpdate.value)

    // Get the last transaction reference for the device BEFORE updating
    val lastTxnRef = acc.calculated.devices.get(address).fold(TxnRef.empty)(_.currentTxnRef)

    // Create the DatapointTransaction using the LAST transaction's details
    val datapointTransaction = DatapointTransaction(
      owner = address,
      transactionType = DatapointUpdateType,
      datapoint = update.datapoint,
      lastTxnOrdinal = lastTxnRef.txnSnapshotOrdinal,
      lastTxnHash = lastTxnRef.txnHash
    )

    // Get the updated list of datapoint transactions for the device
    val updatedDeviceDatapoints = getUpdatedDeviceDatapoints(update.datapoint, acc, address)
    val currentSnapshotOrdinal: SnapshotOrdinal = SnapshotOrdinal(NonNegLong.unsafeFrom(lastSnapshotOrdinal.value.value + 1))

    // Update the device's calculated state with the new transaction details
    val device = DeviceCalculatedState(updatedDeviceDatapoints, TxnRef(currentSnapshotOrdinal, updateHash))
    val devices = acc.calculated.devices.updated(address, device)

    // Add the new transaction to the list of updates
    val updates: List[Signed[DatapointTransaction]] = Signed(datapointTransaction, signedUpdate.proofs) :: acc.onChain.updates.asInstanceOf[List[Signed[DatapointTransaction]]]

    // Return the new DataState
    DataState(
      DatapointUpdateState(updates.asInstanceOf[List[Signed[DatapointTransaction]]]),
      DatapointUpdateCalculatedState(devices)
    )
  }
}
```

## Validations.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/validations/Validations.scala`

```scala
package com.my.water_and_energy_usage.shared_data.validations

import cats.syntax.apply._
import cats.syntax.option._
import com.my.water_and_energy_usage.shared_data.errors.Errors.EmptyUpdate
import com.my.water_and_energy_usage.shared_data.types.Types._
import com.my.water_and_energy_usage.shared_data.validations.TypeValidators._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.Signed

object Validations {
  private def getDeviceByAddress(
    address             : Address,
    maybeCalculatedState: Option[DatapointUpdateCalculatedState]
  ): Option[DeviceCalculatedState] = {
    maybeCalculatedState
      .flatMap(state => state.devices.get(address))
  }

  def validateDatapointUpdate(
    update              : DatapointUpdate,
    maybeCalculatedState: Option[DatapointUpdateCalculatedState]
  ): DataApplicationValidationErrorOr[Unit] = {
    val address = update.address
    val maybeDeviceState = getDeviceByAddress(address, maybeCalculatedState)

    if (update.datapoint.timestamp <= 0) {
      EmptyUpdate.invalid
    } else {
      validateDatapointPrivacyLevel(update.datapoint)
    }
  }

  def validateDatapointUpdateSigned(
    signedUpdate   : Signed[DatapointUpdate],
    calculatedState: DatapointUpdateCalculatedState,
    addresses      : List[Address]
  ): DataApplicationValidationErrorOr[Unit] =
    validateProvidedAddress(addresses, signedUpdate.value.address)
      .productR(validateDatapointUpdate(signedUpdate.value, calculatedState.some))
}

```

## TypeValidators.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/validations/TypeValidators.scala`

```scala
package com.my.water_and_energy_usage.shared_data.validations

import com.my.water_and_energy_usage.shared_data.errors.Errors._
import com.my.water_and_energy_usage.shared_data.types.Types._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import cats.data.Validated

object TypeValidators {

  def validateDatapointPrivacyLevel(datapoint: Datapoint): DataApplicationValidationErrorOr[Unit] =
    Validated.valid(()) // Placeholder: Always returns a valid result

  def validateProvidedAddress(proofAddresses: List[Address], address: Address): DataApplicationValidationErrorOr[Unit] =
    InvalidAddress.unless(proofAddresses.contains(address))

}

```

## Serializers.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/serializers/Serializers.scala`

```scala
package com.my.water_and_energy_usage.shared_data.serializers

import com.my.water_and_energy_usage.shared_data.types.Types.{DatapointUpdate, DatapointUpdateCalculatedState, DatapointUpdateState}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Serializers {
  private def serialize[A: Encoder](
    serializableData: A
  ): Array[Byte] = {
    serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  def serializeUpdate(
    update: DatapointUpdate
  ): Array[Byte] =
    serialize[DatapointUpdate](update)

  def serializeState(
    state: DatapointUpdateState
  ): Array[Byte] =
    serialize[DatapointUpdateState](state)

  def serializeBlock(
    state: Signed[DataApplicationBlock]
  )(implicit e: Encoder[DataUpdate]): Array[Byte] =
    serialize[Signed[DataApplicationBlock]](state)

  def serializeCalculatedState(
    state: DatapointUpdateCalculatedState
  ): Array[Byte] =
    serialize[DatapointUpdateCalculatedState](state)
}

```

## CalculatedStateService.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/calculated_state/CalculatedStateService.scala`

```scala
package com.my.water_and_energy_usage.shared_data.calculated_state

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState
import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState.hash
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash

trait CalculatedStateService[F[_]] {
  def getCalculatedState: F[CalculatedState]

  def setCalculatedState(
    snapshotOrdinal: SnapshotOrdinal,
    state          : DatapointUpdateCalculatedState
  ): F[Boolean]

  def hashCalculatedState(
    state: DatapointUpdateCalculatedState
  ): F[Hash]
}

object CalculatedStateService {
  def make[F[_] : Async]: F[CalculatedStateService[F]] = {
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def getCalculatedState: F[CalculatedState] = stateRef.get

        override def setCalculatedState(
          snapshotOrdinal: SnapshotOrdinal,
          state          : DatapointUpdateCalculatedState
        ): F[Boolean] =
          stateRef.modify { currentState =>
            // Merge current devices with the new ones
            val devices = currentState.state.devices ++ state.devices
            CalculatedState(snapshotOrdinal, DatapointUpdateCalculatedState(devices)) -> true
          }

        override def hashCalculatedState(
          state: DatapointUpdateCalculatedState
        ): F[Hash] = Async[F].delay {
          hash(state)
        }
      }
    }
  }
}

```

## CalculatedState.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/calculated_state/CalculatedState.scala`

```scala
package com.my.water_and_energy_usage.shared_data.calculated_state

import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState
import org.tessellation.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: DatapointUpdateCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(
      SnapshotOrdinal.MinValue,
      DatapointUpdateCalculatedState(Map.empty)
    )
}
```

## Errors.scala
**Path:** `./modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/errors/Errors.scala`

```scala
package com.my.water_and_energy_usage.shared_data.errors

import cats.syntax.all._
import org.tessellation.currency.dataApplication.DataApplicationValidationError
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Errors {
  type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

  val valid: DataApplicationValidationType =
    ().validNec[DataApplicationValidationError]

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType =
      err.invalidNec[Unit]

    def unless(
      cond: Boolean
    ): DataApplicationValidationType =
      if (cond) valid else invalid

    def when(
      cond: Boolean
    ): DataApplicationValidationType =
      if (cond) invalid else valid
  }

  // Define custom validation errors for DatapointUpdate

  case object DatapointInvalid extends DataApplicationValidationError {
    val message = "Datapoint is invalid."
  }

  case object DatapointPrivacyLevelInvalid extends DataApplicationValidationError {
    val message = "Datapoint privacy level is invalid."
  }

  case object EmptyUpdate extends DataApplicationValidationError {
    val message = "Provided an empty update."
  }

  case object InvalidAddress extends DataApplicationValidationError {
    val message = "Provided address is different from proof."
  }

  case object DatapointTimestampOutdated extends DataApplicationValidationError {
    val message = "Datapoint timestamp is older than the latest update timestamp, rejecting."
  }
}

```

## Main.scala
**Path:** `./modules/data_l1/src/main/scala/com/my/water_and_energy_usage/data_l1/Main.scala`

```scala
package com.my.water_and_energy_usage.data_l1

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.LifecycleSharedFunctions
import com.my.water_and_energy_usage.shared_data.calculated_state.CalculatedStateService
import com.my.water_and_energy_usage.shared_data.deserializers.Deserializers
import com.my.water_and_energy_usage.shared_data.serializers.Serializers
import com.my.water_and_energy_usage.shared_data.types.Types._
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

import java.util.UUID

object Main extends CurrencyL1App(
  "currency-data_l1",
  "currency data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {

  private def makeBaseDataApplicationL1Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL1Service[IO] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[IO, DatapointUpdate, DatapointUpdateState, DatapointUpdateCalculatedState] {
      override def validateData(
        state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
        updates: NonEmptyList[Signed[DatapointUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
        ().validNec.pure[IO]  // Simplified validation, always valid

      override def validateUpdate(
        update: DatapointUpdate
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
        ().validNec.pure[IO]  // Simplified validation, always valid

      override def combine(
        state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
        updates: List[Signed[DatapointUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] =
        state.pure[IO]  // For now, just return the current state without modification

      override def routes(implicit context: L1NodeContext[IO]): HttpRoutes[IO] =
        HttpRoutes.empty

      override def dataEncoder: Encoder[DatapointUpdate] =
        implicitly[Encoder[DatapointUpdate]]

      override def dataDecoder: Decoder[DatapointUpdate] =
        implicitly[Decoder[DatapointUpdate]]

      override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] =
        implicitly[Encoder[DatapointUpdateCalculatedState]]

      override def calculatedStateDecoder: Decoder[DatapointUpdateCalculatedState] =
        implicitly[Decoder[DatapointUpdateCalculatedState]]

      override def signedDataEntityDecoder: EntityDecoder[IO, Signed[DatapointUpdate]] =
        circeEntityDecoder

      override def serializeBlock(
        block: Signed[DataApplicationBlock]
      ): IO[Array[Byte]] =
        IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

      override def deserializeBlock(
        bytes: Array[Byte]
      ): IO[Either[Throwable, Signed[DataApplicationBlock]]] =
        IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

      override def serializeState(
        state: DatapointUpdateState
      ): IO[Array[Byte]] =
        IO(Serializers.serializeState(state))

      override def deserializeState(
        bytes: Array[Byte]
      ): IO[Either[Throwable, DatapointUpdateState]] =
        IO(Deserializers.deserializeState(bytes))

      override def serializeUpdate(
        update: DatapointUpdate
      ): IO[Array[Byte]] =
        IO(Serializers.serializeUpdate(update))

      override def deserializeUpdate(
        bytes: Array[Byte]
      ): IO[Either[Throwable, DatapointUpdate]] =
        IO(Deserializers.deserializeUpdate(bytes))

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
      ): IO[Array[Byte]] =
        IO(Serializers.serializeCalculatedState(state))

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): IO[Either[Throwable, DatapointUpdateCalculatedState]] =
        IO(Deserializers.deserializeCalculatedState(bytes))
    }
  )

  private def makeL1Service: IO[BaseDataApplicationL1Service[IO]] = {
    for {
      calculatedStateService <- CalculatedStateService.make[IO]
      dataApplicationL1Service = makeBaseDataApplicationL1Service(calculatedStateService)
    } yield dataApplicationL1Service
  }

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] =
    makeL1Service.asResource.some
}

```

## Main.scala
**Path:** `./modules/l0/src/main/scala/com/my/water_and_energy_usage/l0/Main.scala`

```scala
package com.my.water_and_energy_usage.l0

import cats.data.NonEmptyList
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

import java.util.UUID

object Main
  extends CurrencyL0App(
    "currency-l0",
    "currency L0 node",
    ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
    metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
    tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
  ) {

  private def makeBaseDataApplicationL0Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL0Service[IO] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[IO, DatapointUpdate, DatapointUpdateState, DatapointUpdateCalculatedState] {
        override def genesis: DataState[DatapointUpdateState, DatapointUpdateCalculatedState] =
          DataState(DatapointUpdateState(List.empty), DatapointUpdateCalculatedState(Map.empty))

        override def validateUpdate(
          update: DatapointUpdate
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[IO]  // Simplified validation, always valid

        override def validateData(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: NonEmptyList[Signed[DatapointUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[IO]  // Simplified validation, always valid

        override def combine(
          state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
          updates: List[Signed[DatapointUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] =
          LifecycleSharedFunctions.combine[IO](state, updates)

        override def dataEncoder: Encoder[DatapointUpdate] =
          implicitly[Encoder[DatapointUpdate]]

        override def calculatedStateEncoder: Encoder[DatapointUpdateCalculatedState] =
          implicitly[Encoder[DatapointUpdateCalculatedState]]

        override def dataDecoder: Decoder[DatapointUpdate] =
          implicitly[Decoder[DatapointUpdate]]

        override def calculatedStateDecoder: Decoder[DatapointUpdateCalculatedState] =
          implicitly[Decoder[DatapointUpdateCalculatedState]]

        override def signedDataEntityDecoder: EntityDecoder[IO, Signed[DatapointUpdate]] =
          circeEntityDecoder

        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): IO[Array[Byte]] =
          IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

        override def deserializeBlock(
          bytes: Array[Byte]
        ): IO[Either[Throwable, Signed[DataApplicationBlock]]] =
          IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

        override def serializeState(
          state: DatapointUpdateState
        ): IO[Array[Byte]] =
          IO(Serializers.serializeState(state))

        override def deserializeState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateState]] =
          IO(Deserializers.deserializeState(bytes))

        override def serializeUpdate(
          update: DatapointUpdate
        ): IO[Array[Byte]] =
          IO(Serializers.serializeUpdate(update))

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdate]] =
          IO(Deserializers.deserializeUpdate(bytes))

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
        ): IO[Array[Byte]] =
          IO(Serializers.serializeCalculatedState(state))

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, DatapointUpdateCalculatedState]] =
          IO(Deserializers.deserializeCalculatedState(bytes))
      })

  private def makeL0Service: IO[BaseDataApplicationL0Service[IO]] = {
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

```

## CustomRoutes.scala
**Path:** `./modules/l0/src/main/scala/com/my/water_and_energy_usage/l0/custom_routes/CustomRoutes.scala`

```scala
package com.my.water_and_energy_usage.l0.custom_routes

import cats.MonadThrow
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.calculated_state.CalculatedStateService
import com.my.water_and_energy_usage.shared_data.deserializers.Deserializers
import com.my.water_and_energy_usage.shared_data.types.Types._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import org.http4s._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.tessellation.currency.dataApplication.L0NodeContext
import org.tessellation.currency.schema.currency.DataApplicationPart
import org.tessellation.ext.http4s.AddressVar
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address

case class CustomRoutes[F[_] : Async](
  calculatedStateService: CalculatedStateService[F],
  context               : L0NodeContext[F]
) extends Http4sDsl[F] with PublicRoutes[F] {

  @derive(encoder, decoder)
  case class TransactionResponse(
    data               : String,
    privacyLevel       : String,
    timestamp          : Long,
    txnSnapshotOrdinal : SnapshotOrdinal,
    txnHash            : String,
    lastRef            : TxnRef
  )

  private object TransactionResponse {
    def apply(datapointTransaction: DatapointTransaction, snapshotOrdinal: SnapshotOrdinal, txnRef: String): TransactionResponse = {
      TransactionResponse(
        datapointTransaction.datapoint.data,
        datapointTransaction.datapoint.privacyLevel,
        datapointTransaction.datapoint.timestamp,
        snapshotOrdinal,
        txnRef,
        TxnRef(datapointTransaction.lastTxnOrdinal, datapointTransaction.lastTxnHash)
      )
    }
  }
  
  private def getAddressTransactionsFromState(
    ordinal: SnapshotOrdinal,
    address: Address
  ): F[AddressTransactionsWithLastRef] = {
    val dataApplicationPart: F[Option[DataApplicationPart]] =
      OptionT(context.getCurrencySnapshot(ordinal))
        .getOrRaise(new Exception(s"Could not fetch snapshot: ${ordinal.show}, ${address.show}"))
        .map(_.dataApplication)
    OptionT(dataApplicationPart)
      .semiflatMap(da => MonadThrow[F].fromEither(Deserializers.deserializeState(da.onChainState)))
      .map(_.updates.filter(_.owner === address)) // Directly filter on owner
      .map(_.sortBy(_.lastTxnOrdinal)(Ordering[SnapshotOrdinal].reverse)) // Sort by txnOrdinal
      .mapFilter(txs => txs.lastOption.map(t => AddressTransactionsWithLastRef(TxnRef(t.value.lastTxnOrdinal, t.value.lastTxnHash), txs.map(_.value))))
      .getOrElse(AddressTransactionsWithLastRef(TxnRef.empty, List.empty[DatapointTransaction]))
  }

  private def getTransactionsResponse(
    datapointTransactions: List[DatapointTransaction],
    snapshotOrdinal      : SnapshotOrdinal,
    txnHash              : String
  ): List[TransactionResponse] =
    datapointTransactions.foldLeft(List.empty[TransactionResponse]) { (acc, transaction) =>
      if (acc.isEmpty) {
        acc :+ TransactionResponse(transaction, snapshotOrdinal, txnHash)
      } else {
        acc :+ TransactionResponse(transaction, acc.last.lastRef.txnSnapshotOrdinal, acc.last.lastRef.txnHash)
      }
    }

  private def traverseSnapshotsWithTransactions(
    address        : Address,
    startingOrdinal: SnapshotOrdinal,
    txnHash        : String,
    transactions   : List[TransactionResponse]
  ): F[List[TransactionResponse]] = {
    (address, startingOrdinal, txnHash, transactions).tailRecM {
      case (addr, ord, hash, txns) =>
        getAddressTransactionsFromState(ord, addr).map { addressTransactionsWithLastRef =>
          if (addressTransactionsWithLastRef.txnRef.txnSnapshotOrdinal == SnapshotOrdinal.MinValue) {
            (txns ++ getTransactionsResponse(addressTransactionsWithLastRef.txns, ord, hash)).asRight
          } else {
            (
              addr,
              addressTransactionsWithLastRef.txnRef.txnSnapshotOrdinal,
              addressTransactionsWithLastRef.txnRef.txnHash,
              txns ++ getTransactionsResponse(addressTransactionsWithLastRef.txns, ord, hash)
            ).asLeft
          }
        }
    }
  }


  private def getAllAddressTransactions(
    address: Address
  ): F[List[TransactionResponse]] = {
    calculatedStateService.getCalculatedState.flatMap { calculatedState =>
      calculatedState.state.devices
        .get(address)
        .fold(List.empty[TransactionResponse].pure) { deviceCalculatedState =>
          val txnSnapshotOrdinal: SnapshotOrdinal = deviceCalculatedState.currentTxnRef.txnSnapshotOrdinal
          val txnHash: String = deviceCalculatedState.currentTxnRef.txnHash
          traverseSnapshotsWithTransactions(address, txnSnapshotOrdinal, txnHash, List.empty[TransactionResponse])
            .handleErrorWith(err => new Exception(s"Error when getting all address transactions: ${err.getMessage}").raiseError[F, List[TransactionResponse]])
        }
    }
  }


  private def getAllDevices: F[Response[F]] = {
    calculatedStateService.getCalculatedState
      .flatMap(value => Ok(value.state.devices))
  }

  private def getDeviceByAddress(
    address: Address
  ): F[Response[F]] =
    calculatedStateService.getCalculatedState
      .flatMap { value =>
        value.state.devices.get(address)
          .map(deviceInfo => Ok(deviceInfo.datapoints))
          .getOrElse(NotFound())
      }

  private def getDeviceTransactions(
    address: Address
  ): F[Response[F]] = {
    getAllAddressTransactions(address)
      .map(_.sortBy(_.txnSnapshotOrdinal)(Ordering[SnapshotOrdinal].reverse))
      .flatMap(Ok(_))

  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "addresses" => getAllDevices
    case GET -> Root / "addresses" / AddressVar(address) => getDeviceByAddress(address)
    case GET -> Root / "addresses" / AddressVar(address) / "transactions" => getDeviceTransactions(address)
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}

```

## Main.scala
**Path:** `./modules/l1/src/main/scala/com/my/water_and_energy_usage/l1/Main.scala`

```scala
package com.my.water_and_energy_usage.l1

import org.tessellation.BuildInfo
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}

import java.util.UUID

object Main extends CurrencyL1App(
  "currency-l1",
  "currency L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version),
  tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version)
) {}

```

