package com.my.ai_metagraph.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.effect.std.Env
import cats.syntax.all._
import com.my.ai_metagraph.shared_data.Utils.getAllAddressesFromProofs
import com.my.ai_metagraph.shared_data.combiners.Combiners.combineDatapointUpdate
import com.my.ai_metagraph.shared_data.types.Types._
import com.my.ai_metagraph.shared_data.validations.Validations.{validateDatapointUpdate, validateDatapointUpdateSigned}
import com.my.ai_metagraph.shared_data.external_apis.FirestoreClient
import io.circe.syntax._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {

  private def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  /**
   * Validates a single `DatapointUpdate` by checking its structure and content.
   *
   * @param update The update that needs to be validated.
   * @return Validation result wrapped in F[_]
   */
  def validateUpdate[F[_] : Async](
    update: DatapointUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    validateDatapointUpdate(update) // Perform validation based on the provided data structure
  }

  /**
   * Validates a list of signed `DatapointUpdate`s against the current state.
   */
  def validateData[F[_] : Async : SecurityProvider](
    oldState: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates: NonEmptyList[Signed[DatapointUpdate]]
  ): F[DataApplicationValidationErrorOr[Unit]] =
    updates.traverse { signedUpdate =>
      getAllAddressesFromProofs(signedUpdate.proofs)
        .flatMap(addresses => Async[F].delay(validateDatapointUpdateSigned(signedUpdate, oldState.calculated, addresses)))
    }.map(_.reduce)

  /**
   * Combines the current state with new updates, applying them to the state and generating a new snapshot.
   */
  def combine[F[_] : Async : Env](
    oldState: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates: List[Signed[DatapointUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] = {
    val newState = DataState(
      DatapointUpdateState(List.empty), 
      DatapointUpdateCalculatedState(oldState.calculated.devices)
    )

    val lastSnapshotOrdinal: F[SnapshotOrdinal] = context.getLastCurrencySnapshot.flatMap {
      case Some(value) => value.ordinal.pure[F]
      case None =>
        val message = "Could not retrieve the last snapshot ordinal."
        logger.error(message) >> new Exception(message).raiseError[F, SnapshotOrdinal]
    }

    if (updates.isEmpty) {
      logger.info("No updates in the current snapshot, returning the current state unchanged").as(newState)
    } else {
      updates.foldLeftM(newState) { (acc, signedUpdate) =>
        lastSnapshotOrdinal.flatMap { ordinal =>
          
          // Create the dataTypesHash and privateDataHash
          val dataTypesHash = createDataTypesHash(signedUpdate.value.datapoint.data) // Generate hash for the types of data being shared
          val privateDataHash = createPrivateDataHash(signedUpdate.value) // Generate hash for the private data

          // Send the data to Firestore
          FirestoreClient.sendToFirestore(signedUpdate.value, dataTypesHash, privateDataHash).flatMap { _ =>
            
            // Now, update the chain state after successful Firestore storage
            val updatedDatapoint = signedUpdate.value.datapoint.copy(hash = privateDataHash)
            val updatedTransaction = DatapointTransaction(
              owner = signedUpdate.value.address,
              datapoint = updatedDatapoint,
              lastTxnOrdinal = ordinal,
              lastTxnHash = ""  // Assuming no previous transaction hash, or replace with actual
            )
            
            acc.copy(state = acc.state.copy(updates = acc.state.updates :+ Signed(updatedTransaction, signedUpdate.proofs))).pure[F]
          }
        }
      }
    }
  }

  /**
   * Generates a hash based on the data types being shared (the sharing preferences).
   * This ensures that requests for the same types of data can be matched to updates with the same hash.
   */
  def createDataTypesHash(data: DatapointData): String = {
    // Convert the data fields that have a sharing preference (boolean fields) into a hashable string
    val dataTypesJson = Map(
      "steps" -> data.steps.isDefined,
      "sleep_hours" -> data.sleep_hours.isDefined,
      "calories_consumed" -> data.calories_consumed.isDefined,
      "calories_expended" -> data.calories_expended.isDefined,
      "exercise_minutes" -> data.exercise_minutes.isDefined,
      "resting_heart_rate" -> data.resting_heart_rate.isDefined,
      "heart_rate" -> data.heart_rate.isDefined,
      "blood_pressure" -> data.blood_pressure.isDefined
    ).asJson.noSpaces
    
    // Hash the JSON string to create a consistent fingerprint for the shared data types
    java.security.MessageDigest.getInstance("SHA-256").digest(dataTypesJson.getBytes).map("%02x".format(_)).mkString
  }

  /**
   * Generates a hash of the private data, including its structure and content.
   */
  def createPrivateDataHash(update: DatapointUpdate): String = {
    val dataJson = update.datapoint.data.asJson.noSpaces

    // Combine with address and timestamp for uniqueness
    val uniqueData = s"${update.address.toString}-${update.datapoint.timestamp}-$dataJson"
    
    // Hash the unique string
    java.security.MessageDigest.getInstance("SHA-256").digest(uniqueData.getBytes).map("%02x".format(_)).mkString
  }

}
