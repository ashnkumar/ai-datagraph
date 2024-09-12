package com.my.ai_datagraph.shared_data.validations

import cats.syntax.all._
import com.my.ai_datagraph.shared_data.errors.Errors._
import com.my.ai_datagraph.shared_data.types.Types._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Validations {

  // Main validation for DataUpdateRaw in the L1 layer
  def validateUpdateL1(
    update: DataUpdateRaw
  ): DataApplicationValidationErrorOr[Unit] = {
    validateOptionalFields(update)
  }

  // 1. Validate fields with logical ranges
  def validateOptionalFields(
    update: DataUpdateRaw
  ): DataApplicationValidationErrorOr[Unit] = {
    update.shared_data.toList.map {
      case ("age", value)         => validateAge(value)
      case ("height", value)      => validateHeight(value)
      case ("weight", value)      => validateWeight(value)
      case ("steps", value)       => validateSteps(value)
      case ("calories", value)    => validateCalories(value)
      case ("heart_rate", value)  => validateHeartRate(value)
      case ("sleep_hours", value) => validateSleepHours(value)
      case (key, _)               => ().validNec // Pass for any unknown fields
    }.sequence_.map(_ => ())
  }

  // Validate that age is between 18 and 80
  def validateAge(value: String): DataApplicationValidationErrorOr[Unit] = {
    val age = value.toIntOption.getOrElse(-1)
    AgeOutOfRange.whenA(age < 18 || age > 80)
  }

  // Validate that height is between 4 feet (122 cm) and 7 feet (213 cm)
  def validateHeight(value: String): DataApplicationValidationErrorOr[Unit] = {
    val height = value.toDoubleOption.getOrElse(-1.0)
    HeightOutOfRange.whenA(height < 122.0 || height > 213.0)
  }

  // Validate that weight is between 40 kg and 200 kg
  def validateWeight(value: String): DataApplicationValidationErrorOr[Unit] = {
    val weight = value.toDoubleOption.getOrElse(-1.0)
    WeightOutOfRange.whenA(weight < 40.0 || weight > 200.0)
  }

  // Validate steps between 0 and 100,000 (reasonable daily step count)
  def validateSteps(value: String): DataApplicationValidationErrorOr[Unit] = {
    val steps = value.toIntOption.getOrElse(-1)
    StepsOutOfRange.whenA(steps < 0 || steps > 100000)
  }

  // Validate calories burned between 0 and 10,000 (reasonable daily range)
  def validateCalories(value: String): DataApplicationValidationErrorOr[Unit] = {
    val calories = value.toIntOption.getOrElse(-1)
    CaloriesOutOfRange.whenA(calories < 0 || calories > 10000)
  }

  // Validate heart rate between 40 and 200 bpm
  def validateHeartRate(value: String): DataApplicationValidationErrorOr[Unit] = {
    val heartRate = value.toIntOption.getOrElse(-1)
    HeartRateOutOfRange.whenA(heartRate < 40 || heartRate > 200)
  }

  // Validate sleep hours between 0 and 24 hours
  def validateSleepHours(value: String): DataApplicationValidationErrorOr[Unit] = {
    val sleepHours = value.toDoubleOption.getOrElse(-1.0)
    SleepHoursOutOfRange.whenA(sleepHours < 0.0 || sleepHours > 24.0)
  }
}
