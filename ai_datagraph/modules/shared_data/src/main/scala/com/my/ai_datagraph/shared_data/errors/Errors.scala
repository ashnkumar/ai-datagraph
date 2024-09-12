package com.my.ai_datagraph.shared_data.errors

import cats.syntax.all._
import org.tessellation.currency.dataApplication.DataApplicationValidationError
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Errors {
  type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

  val valid: DataApplicationValidationType = ().validNec[DataApplicationValidationError]

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType = err.invalidNec[Unit]

    def unlessA(cond: Boolean): DataApplicationValidationType = if (cond) valid else invalid

    def whenA(cond: Boolean): DataApplicationValidationType = if (cond) invalid else valid
  }

  case object InvalidTimestamp extends DataApplicationValidationError {
    val message = "New data update has an invalid timestamp. Must be greater than the latest timestamp."
  }

  case object DuplicateTransactionHash extends DataApplicationValidationError {
    val message = "Duplicate transaction hash detected."
  }

  case object InvalidAgeRange extends DataApplicationValidationError {
    val message = "Age must be between 18 and 80."
  }

  case object InvalidStepsRange extends DataApplicationValidationError {
    val message = "Steps must be between 0 and 10000 per hour."
  }

  case object InvalidHeartRateRange extends DataApplicationValidationError {
    val message = "Resting heart rate must be between 40 and 100 BPM."
  }

  case object InvalidSleepHours extends DataApplicationValidationError {
    val message = "Sleep hours must be between 0 and 24."
  }

  case object InvalidWeightRange extends DataApplicationValidationError {
    val message = "Weight must be between 100 and 200 pounds."
  }

  case object InvalidHeightRange extends DataApplicationValidationError {
    val message = "Height must be between 5'0\" and 6'1\"."
  }

  case object InvalidTimestampFormat extends DataApplicationValidationError {
    val message = "Timestamp must be a valid long integer representing milliseconds."
  }

  case object InvalidTransactionHashFormat extends DataApplicationValidationError {
    val message = "Transaction hash must be a valid string."
  }
}
