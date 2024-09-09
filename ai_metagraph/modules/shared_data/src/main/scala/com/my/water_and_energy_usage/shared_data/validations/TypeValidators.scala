package com.my.water_and_energy_usage.shared_data.validations

import com.my.water_and_energy_usage.shared_data.errors.Errors._
import com.my.water_and_energy_usage.shared_data.types.Types._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.address.Address
import cats.data.Validated

object TypeValidators {

  // Placeholder validation for Datapoint, returns valid result always
  // In the future, we can add more complex validations here if needed.
  def validateDatapoint(datapoint: Datapoint): DataApplicationValidationErrorOr[Unit] =
    Validated.valid(()) // Always returns a valid result for now

  // Placeholder validation for provided address, returns valid result always
  def validateProvidedAddress(proofAddresses: List[Address], address: Address): DataApplicationValidationErrorOr[Unit] =
    Validated.valid(()) // Always returns a valid result for now

  // Validation for the hash in Datapoint. In future, we can add checks to verify the integrity of the hash.
  def validateDatapointHash(datapoint: Datapoint): DataApplicationValidationErrorOr[Unit] = 
    if (datapoint.hash.isEmpty) {
      EmptyUpdate.invalid // Example error for empty hash
    } else {
      Validated.valid(()) // Always returns valid for now
    }
}
