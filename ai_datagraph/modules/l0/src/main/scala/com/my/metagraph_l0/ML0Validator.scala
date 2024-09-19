package com.my.metagraph_l0

import cats.effect.Async
import cats.syntax.all._

import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.security.Hasher
import org.tessellation.security.signature.Signed

import com.my.shared_data.lib.UpdateValidator
import com.my.shared_data.ValidatorRules
import com.my.shared_data.schema.Updates.DataUpdateRaw
import com.my.shared_data.schema.{CalculatedState, OnChainState}

trait ML0Validator[F[_], U, T] extends UpdateValidator[F, U, T]

object ML0Validator {

  type TX = DataUpdateRaw
  type DS = DataState[OnChainState, CalculatedState]

  def make[F[_]: Async: Hasher]: ML0Validator[F, Signed[TX], DS] =
    new ML0Validator[F, Signed[TX], DS] {

      override def verify(state: DS, signedUpdate: Signed[TX]): F[DataApplicationValidationErrorOr[Unit]] =
        validateDataUpdateRaw(signedUpdate.value)(state.onChain)

      private def validateDataUpdateRaw(
        update: DataUpdateRaw
      ): OnChainState => F[DataApplicationValidationErrorOr[Unit]] = (state: OnChainState) =>
        for {
          res1 <- ValidatorRules.validateDataUpdateRaw(update).pure[F]
          res2 <- ValidatorRules.validateDataUpdateRawL0(update, state).pure[F]
        } yield List(res1, res2).combineAll
    }
}
