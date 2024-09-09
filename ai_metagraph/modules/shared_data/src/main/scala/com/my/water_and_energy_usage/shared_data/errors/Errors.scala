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

  case object EmptyUpdate extends DataApplicationValidationError {
    val message = "Provided an empty update."
  }

  case object StepsNotValid extends DataApplicationValidationError {
    val message = "Steps count is not valid."
  }

  case object SleepHoursNotValid extends DataApplicationValidationError {
    val message = "Sleep hours value is not valid."
  }

  case object CaloriesNotValid extends DataApplicationValidationError {
    val message = "Calories value is not valid."
  }

  case object ExerciseMinutesNotValid extends DataApplicationValidationError {
    val message = "Exercise minutes value is not valid."
  }

  case object RestingHeartRateNotValid extends DataApplicationValidationError {
    val message = "Resting heart rate value is not valid."
  }

  case object HeartRateNotValid extends DataApplicationValidationError {
    val message = "Heart rate value is not valid."
  }

  case object BloodPressureNotValid extends DataApplicationValidationError {
    val message = "Blood pressure value is not valid."
  }

  case object TimestampOutOfOrder extends DataApplicationValidationError {
    val message = "The timestamp of the new data update is out of order."
  }
}
