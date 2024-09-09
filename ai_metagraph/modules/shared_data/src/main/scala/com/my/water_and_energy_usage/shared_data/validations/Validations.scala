package com.my.ai_metagraph.shared_data.validations

import cats.syntax.apply._
import cats.syntax.option._
import com.my.ai_metagraph.shared_data.errors.Errors._
import com.my.ai_metagraph.shared_data.types.Types._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.Signed
import cats.effect.Async

object Validations {

  // Helper function to retrieve the device's calculated state by its address
  private def getDeviceByAddress(
    address: Address,
    maybeCalculatedState: Option[DatapointUpdateCalculatedState]
  ): Option[DeviceCalculatedState] = {
    maybeCalculatedState.flatMap(state => state.devices.get(address))
  }

  // Validation performed by the data_l1 node's validateUpdate function
  def validateDatapointUpdate(
    update: DatapointUpdate,
    maybeCalculatedState: Option[DatapointUpdateCalculatedState] = None
  ): DataApplicationValidationErrorOr[Unit] = {

    // Basic validation checks for fields like timestamp, address
    val timestampValid = validateTimestamp(update.datapoint.timestamp)
    val walletAddressValid = validateWalletAddress(update.address)

    // If there's calculated state, validate it
    val maybeAggregatedState = getDeviceByAddress(update.address, maybeCalculatedState)

    // Field-level validations (e.g., steps, sleep_hours, calories, etc.)
    val stepsValid = validateOptionalField(update.datapoint.data.steps, validateSteps)
    val sleepHoursValid = validateOptionalField(update.datapoint.data.sleep_hours, validateSleepHours)
    val caloriesConsumedValid = validateOptionalField(update.datapoint.data.calories_consumed, validateCaloriesConsumed)
    val caloriesExpendedValid = validateOptionalField(update.datapoint.data.calories_expended, validateCaloriesExpended)
    val exerciseMinutesValid = validateOptionalField(update.datapoint.data.exercise_minutes, validateExerciseMinutes)
    val restingHeartRateValid = validateOptionalField(update.datapoint.data.resting_heart_rate, validateRestingHeartRate)
    val heartRateValid = validateOptionalField(update.datapoint.data.heart_rate, validateHeartRate)
    val bloodPressureValid = validateOptionalField(update.datapoint.data.blood_pressure, validateBloodPressure)

    // Ensure timestamp is newer than previous state
    val timestampChecks = maybeAggregatedState.map { aggregatedState =>
      validateTimestampAgainstPreviousState(aggregatedState, update.datapoint.timestamp)
    }.getOrElse(().validNec)

    // Combine all validations
    timestampValid
      .productR(walletAddressValid)
      .productR(stepsValid)
      .productR(sleepHoursValid)
      .productR(caloriesConsumedValid)
      .productR(caloriesExpendedValid)
      .productR(exerciseMinutesValid)
      .productR(restingHeartRateValid)
      .productR(heartRateValid)
      .productR(bloodPressureValid)
      .productR(timestampChecks)
  }

  def validateDatapointUpdateSigned(
    signedUpdate: Signed[DatapointUpdate],
    calculatedState: DatapointUpdateCalculatedState,
    addresses: List[Address]
  ): DataApplicationValidationErrorOr[Unit] = {

    // Ensure the address matches the proof provided
    validateProvidedAddress(addresses, signedUpdate.value.address)
      .productR {
        val maybeAggregatedState = getDeviceByAddress(signedUpdate.value.address, calculatedState.some)

        // Validate timestamp
        val timestampValid = maybeAggregatedState.map { aggregatedState =>
          validateTimestampAgainstPreviousState(aggregatedState, signedUpdate.value.datapoint.timestamp)
        }.getOrElse(().validNec)

        // Combine all checks
        timestampValid
      }
  }

  // Simple timestamp check against the previous state
  def validateTimestampAgainstPreviousState(
    aggregatedState: DeviceCalculatedState,
    newTimestamp: Long
  ): DataApplicationValidationErrorOr[Unit] = {
    TimestampOutOfOrder.when(newTimestamp <= aggregatedState.lastRecordedTimestamp)
  }

  // Generic helper for optional field validation
  def validateOptionalField[T](maybeField: Option[T], validateFunc: T => DataApplicationValidationErrorOr[Unit]): DataApplicationValidationErrorOr[Unit] = {
    maybeField.map(validateFunc).getOrElse(().validNec)
  }
}
