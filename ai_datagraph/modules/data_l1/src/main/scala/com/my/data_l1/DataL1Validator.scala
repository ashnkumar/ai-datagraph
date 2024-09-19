package com.my.data_l1

import cats.effect.Async
import cats.syntax.all._

import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

import com.my.shared_data.schema.Updates.DataUpdateRaw
import com.my.shared_data.ValidatorRules

trait DataL1Validator[F[_]] {
  def validateUpdate(update: DataUpdateRaw): F[DataApplicationValidationErrorOr[Unit]]
}

object DataL1Validator {

  def make[F[_]: Async]: DataL1Validator[F] = new DataL1Validator[F] {
    override def validateUpdate(update: DataUpdateRaw): F[DataApplicationValidationErrorOr[Unit]] = {
      // Validate the update using ValidatorRules
      ValidatorRules.validateDataUpdateRaw(update).pure[F]
    }
  }
}