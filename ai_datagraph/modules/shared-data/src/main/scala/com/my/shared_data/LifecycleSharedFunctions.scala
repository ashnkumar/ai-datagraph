package com.my.shared_data

import cats.effect.Async
import cats.syntax.all._
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.{Hasher, SecurityProvider}
import org.tessellation.security.signature.Signed
import org.tessellation.security.hash.Hash

import com.my.shared_data.lib.PostgresService
import com.my.shared_data.schema.Updates.{DataUpdateRaw, DataUpdateOnChain, PrivateData, PublicData}
import com.my.shared_data.schema.{CalculatedState, OnChainState}

import org.typelevel.log4cats.Logger
import io.circe.Json
import org.tessellation.json.JsonSerializer

object LifecycleSharedFunctions {

  def combine[F[_]: Async: Logger: JsonSerializer](
    inState: DataState[OnChainState, CalculatedState],
    updates: List[Signed[DataUpdateRaw]],
    postgresService: PostgresService[F]
  )(implicit context: L0NodeContext[F], securityProvider: SecurityProvider[F], hasher: Hasher[F]): F[DataState[OnChainState, CalculatedState]] = {
    
    println(s"Starting combine with inState: $inState and updates: $updates")

    for {
      epochProgress <- context.getLastCurrencySnapshot.flatMap {
        case Some(value) => 
          println(s"Epoch progress found: ${value.epochProgress}")
          value.epochProgress.next.pure[F]
        case None =>
          val message = "Could not get the epochProgress from currency snapshot. lastCurrencySnapshot not found"
          println(message)
          new Exception(message).raiseError[F, EpochProgress]
      }

      processedState <- if (updates.isEmpty) {
        println("No updates to process, proceeding with processCalculatedState.")
        processCalculatedState(inState, epochProgress)
      } else {
        println(s"Processing updates: $updates")
        updates.foldLeftM(inState) { case (currentState, signedUpdate) =>
          for {
            updatedState <- processUpdate(currentState, signedUpdate, epochProgress, postgresService)
            finalState   <- processCalculatedState(updatedState, epochProgress)
          } yield finalState
        }
      }
    } yield {
      println(s"Final processed state: $processedState")
      processedState
    }
  }

  private def processCalculatedState[F[_]: Async](
    currentState: DataState[OnChainState, CalculatedState],
    epochProgress: EpochProgress
  ): F[DataState[OnChainState, CalculatedState]] = {
    println(s"Processing CalculatedState with currentState: $currentState and epochProgress: $epochProgress")
    // Implement your logic for updating the CalculatedState, e.g., updating rewards
    currentState.pure[F]
  }

  private def processUpdate[F[_]: Async: SecurityProvider: Hasher: Logger: JsonSerializer](
    currentState: DataState[OnChainState, CalculatedState],
    signedUpdate: Signed[DataUpdateRaw],
    epochProgress: EpochProgress,
    postgresService: PostgresService[F]
  ): F[DataState[OnChainState, CalculatedState]] = {

    println(s"Processing update with currentState: $currentState and signedUpdate: $signedUpdate")

    for {
      // Extract the public data from the update
      publicData <- signedUpdate.value.publicData.pure[F]
      _ = println(s"Extracted public data: $publicData")

      // Extract the private data from the update
      privateData <- signedUpdate.value.privateData.pure[F]
      _ = println(s"Extracted private data: $privateData")

      // Generate a hash of the private data
      privateDataHash <- hashPrivateData(privateData)
      privateDataHashString = privateDataHash.value
      _ = println(s"Generated hash of private data: $privateDataHashString")

      // Save the private data to Postgres
      _ <- postgresService.savePrivateData(privateDataHashString, privateData)
      _ = println(s"Private data saved to Postgres with hash: $privateDataHashString")

      // Create a new on-chain update with the hash of the private data
      onChainUpdate = DataUpdateOnChain(
        address = publicData.address,
        timestamp = publicData.timestamp,
        fields = publicData.fields,
        privateDataHash = privateDataHash
      )
      _ = println(s"Created on-chain update: $onChainUpdate")

      // Update the OnChainState with the new update
      newOnChain = currentState.onChain.copy(
        dataUpdates = currentState.onChain.dataUpdates + (privateDataHashString -> onChainUpdate)
      )
      _ = println(s"Updated OnChainState: $newOnChain")

      // Update the CalculatedState if necessary
      newCalculated = currentState.calculated
      _ = println(s"CalculatedState remains unchanged: $newCalculated")

    } yield DataState(newOnChain, newCalculated)
  }

  private def hashPrivateData[F[_]: Async: Hasher: JsonSerializer](privateData: PrivateData): F[Hash] = {
    println(s"Hashing private data: $privateData")

    for {
      // Serialize privateData to bytes
      privateDataBytes <- JsonSerializer[F].serialize(privateData)
      _ = println(s"Serialized private data to bytes: $privateDataBytes")

      // Hash the bytes
      hash <- Hasher[F].hash(privateDataBytes)
      _ = println(s"Generated hash: $hash")
    } yield hash
  }
}
