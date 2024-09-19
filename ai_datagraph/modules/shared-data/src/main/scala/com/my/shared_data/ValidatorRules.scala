package com.my.shared_data

import cats.data.ValidatedNec
import cats.syntax.all._
import org.tessellation.currency.dataApplication.DataApplicationValidationError
import com.my.shared_data.schema.Updates.{DataUpdateRaw, PrivateData, PublicData}
import com.my.shared_data.schema.OnChainState

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

object ValidatorRules {

  type ValidationResult[A] = ValidatedNec[DataApplicationValidationError, A]

  def validateDataUpdateRaw(update: DataUpdateRaw): ValidationResult[Unit] = {
    val addressValid = if (update.publicData.address.nonEmpty) ().validNec else Errors.InvalidAddress.invalidNec
    val fieldsValid  = if (update.publicData.fields.nonEmpty) ().validNec else Errors.FieldsCannotBeEmpty.invalidNec
    val optionalFieldsValid = validateOptionalFields(update.privateData)

    (addressValid, fieldsValid, optionalFieldsValid).mapN((_, _, _) => ())
  }

  private def validateOptionalFields(privateData: PrivateData): ValidationResult[Unit] = {
    privateData.sensitiveInfo.toList.map {
      case ("age", value)         => validateAge(value)
      case ("height", value)      => validateHeight(value)
      case ("weight", value)      => validateWeight(value)
      case ("steps", value)       => validateSteps(value)
      case ("calories", value)    => validateCalories(value)
      case ("heart_rate", value)  => validateHeartRate(value)
      case ("sleep_hours", value) => validateSleepHours(value)
      case (_, _)                 => ().validNec // Pass for any unknown fields
    }.sequence_.map(_ => ())
  }

  // Validate that age is between 18 and 80
  private def validateAge(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(age) if age >= 18 && age <= 80 => ().validNec
      case _ => Errors.InvalidAge.invalidNec
    }
  }

  // Validate that height is between 122 cm and 213 cm
  private def validateHeight(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(height) if height >= 122 && height <= 213 => ().validNec
      case _ => Errors.InvalidHeight.invalidNec
    }
  }

  // Validate that weight is between 40 kg and 200 kg
  private def validateWeight(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(weight) if weight >= 40 && weight <= 200 => ().validNec
      case _ => Errors.InvalidWeight.invalidNec
    }
  }

  // Validate steps between 0 and 100,000
  private def validateSteps(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(steps) if steps >= 0 && steps <= 100000 => ().validNec
      case _ => Errors.InvalidSteps.invalidNec
    }
  }

  // Validate calories burned between 0 and 10,000
  private def validateCalories(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(calories) if calories >= 0 && calories <= 10000 => ().validNec
      case _ => Errors.InvalidCalories.invalidNec
    }
  }

  // Validate heart rate between 40 and 200 bpm
  private def validateHeartRate(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(heartRate) if heartRate >= 40 && heartRate <= 200 => ().validNec
      case _ => Errors.InvalidHeartRate.invalidNec
    }
  }

  // Validate sleep hours between 0 and 24 hours
  private def validateSleepHours(value: String): ValidationResult[Unit] = {
    value.toIntOption match {
      case Some(sleepHours) if sleepHours >= 0 && sleepHours <= 24 => ().validNec
      case _ => Errors.InvalidSleepHours.invalidNec
    }
  }

  def validateDataUpdateRawL0(update: DataUpdateRaw, onChainState: OnChainState): ValidationResult[Unit] = {
    // Implement any L0-specific validation, for now, we return valid
    ().validNec
  }

  object Errors {

    @derive(decoder, encoder)
    case object InvalidAddress extends DataApplicationValidationError {
      val message: String = "Invalid address"
    }

    @derive(decoder, encoder)
    case object FieldsCannotBeEmpty extends DataApplicationValidationError {
      val message: String = "Fields cannot be empty"
    }

    @derive(decoder, encoder)
    case object InvalidAge extends DataApplicationValidationError {
      val message: String = "Invalid age value; must be between 18 and 80"
    }

    @derive(decoder, encoder)
    case object InvalidHeight extends DataApplicationValidationError {
      val message: String = "Invalid height value; must be between 122 cm and 213 cm"
    }

    @derive(decoder, encoder)
    case object InvalidWeight extends DataApplicationValidationError {
      val message: String = "Invalid weight value; must be between 40 kg and 200 kg"
    }

    @derive(decoder, encoder)
    case object InvalidSteps extends DataApplicationValidationError {
      val message: String = "Invalid steps value; must be between 0 and 100,000"
    }

    @derive(decoder, encoder)
    case object InvalidCalories extends DataApplicationValidationError {
      val message: String = "Invalid calories value; must be between 0 and 10,000"
    }

    @derive(decoder, encoder)
    case object InvalidHeartRate extends DataApplicationValidationError {
      val message: String = "Invalid heart rate value; must be between 40 and 200 bpm"
    }

    @derive(decoder, encoder)
    case object InvalidSleepHours extends DataApplicationValidationError {
      val message: String = "Invalid sleep hours value; must be between 0 and 24"
    }
  }
}
