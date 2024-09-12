package com.my.ai_datagraph.shared_data

import cats.effect.Async
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.types.Types._
import com.my.ai_datagraph.shared_data.validations.Validations._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  
  // Create a logger for debugging purposes
  private def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  // Function to validate updates in data_l1, calls validation logic from Validations.scala
  def validateUpdate[F[_]: Async](
    update: DataUpdateRaw
  ): F[DataApplicationValidationErrorOr[Unit]] = Async[F].delay {
    // Call the validation logic for DataUpdateRaw
    logger[F].info(s"Validating data update: $update") >>
      validateSharedFields(update.shared_data)
  }

  def validateData[F[_]: Async](
    state  : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates: NonEmptyList[Signed[DataUpdateRaw]]
  ): F[DataApplicationValidationErrorOr[Unit]] = {

    // Iterate through the updates and validate each
    updates.traverse { signedUpdate =>
      val update = signedUpdate.value

      // Check if the timestamp of the new data update is greater than the latest timestamp in the state
      val latestTimestamp = state.calculated.updates.get(update.address).map(_.datapoint.timestamp).getOrElse(0L)
      val isValidTimestamp = update.shared_data.get("timestamp").exists(ts => ts.toLongOption.exists(_ > latestTimestamp))

      // Ensure the transaction hash is unique within the state
      val isUniqueHash = !state.onChain.updates.exists { txn =>
        txn.value.datapoint.hash == update.shared_data.getOrElse("hash", "")
      }

      // Perform validations
      val timestampValidation = InvalidTimestamp.whenA(!isValidTimestamp)
      val transactionHashValidation = DuplicateTransactionHash.whenA(!isUniqueHash)

      // Combine validations
      (timestampValidation, transactionHashValidation).mapN((_, _) => ())
    }.map(_.reduce)
  }

  // Combine function that transforms the state with validated updates
  def combine[F[_]: Async](
    currentState: DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    updates: List[Signed[DataUpdateRaw]],
    postgresService: PostgresService[F] // Inject the PostgresService
  )(implicit context: L0NodeContext[F]): F[DataState[DatapointUpdateState, DatapointUpdateCalculatedState]] = {

    updates.foldLeftM(currentState) { (accState, signedUpdate) =>
      val sharedFields = signedUpdate.value.shared_data.keys.toList

      // Create a new Datapoint with the current timestamp and the shared fields
      val datapoint = Datapoint(
        hash = java.util.Base64.getEncoder.encodeToString(sharedFields.mkString(",").getBytes),
        timestamp = System.currentTimeMillis(),
        sharedFields = sharedFields
      )

      val address = signedUpdate.value.address

      // Insert the new datapoint into the PostgreSQL `data_updates` table
      postgresService.insertDatapointUpdate(
        hash = datapoint.hash,
        privateData = datapoint,
        timestamp = datapoint.timestamp,
        address = address
      ).flatMap { _ =>

        // Create a new DatapointTransaction with the updated ordinal and hash
        val newTransaction = DatapointTransaction(
          owner = address,
          datapoint = datapoint,
          lastTxnOrdinal = SnapshotOrdinal(NonNegLong.unsafeFrom(accState.onChain.updates.size.toLong)),
          lastTxnHash = datapoint.hash
        )

        // Update the on-chain state
        val newOnChain = accState.onChain.copy(
          updates = accState.onChain.updates :+ Signed(newTransaction, signedUpdate.proofs)
        )

        // Update the calculated state
        val updatedCalculated = accState.calculated.copy(
          updates = accState.calculated.updates + (newTransaction.owner -> DatapointUpdate(newTransaction.owner, datapoint))
        )

        // Return the updated state
        DataState(newOnChain, updatedCalculated).pure[F]
      }
    }
  }
}
