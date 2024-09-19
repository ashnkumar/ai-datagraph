package com.my.metagraph_l0

import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.types.numeric.PosLong
import org.tessellation.currency.dataApplication.DataCalculatedState
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.infrastructure.consensus.trigger.{ConsensusTrigger, EventTrigger, TimeTrigger}
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Balance
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.schema.transaction.{RewardTransaction, Transaction, TransactionAmount}
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.my.shared_data.schema.CalculatedState

import scala.collection.immutable.{Map, SortedMap, SortedSet}

object ML0Rewards {

  def make[F[_]: Async](): Rewards[F, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent] =
    (
      lastArtifact: Signed[CurrencyIncrementalSnapshot],
      balances: SortedMap[Address, Balance],
      transactions: SortedSet[Signed[Transaction]],
      trigger: ConsensusTrigger,
      events: Set[CurrencySnapshotEvent],
      maybeCalculatedState: Option[DataCalculatedState]
    ) => {

      def noRewards: F[SortedSet[RewardTransaction]] = SortedSet.empty[RewardTransaction].pure[F]

      def buildRewards(
        calculatedState: CalculatedState,
        currentEpochProgress: EpochProgress
      ): F[SortedSet[RewardTransaction]] = {
        val rewardTransactions = calculatedState.rewardsToDistribute.map {
          case (address, amount) =>
            RewardTransaction(
              address,
              TransactionAmount(PosLong.unsafeFrom(amount))
            )
        }.toList

        SortedSet.from(rewardTransactions).pure[F]
      }

      trigger match {
        case EventTrigger => noRewards
        case TimeTrigger =>
          val currentEpochProgress: EpochProgress = lastArtifact.epochProgress.next
          maybeCalculatedState match {
            case None => noRewards
            case Some(calculatedState: CalculatedState) => buildRewards(calculatedState, currentEpochProgress)
            case Some(_) => noRewards
          }
      }
    }
}
