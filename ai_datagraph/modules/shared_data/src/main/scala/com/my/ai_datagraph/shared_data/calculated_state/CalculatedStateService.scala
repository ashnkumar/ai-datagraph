package com.my.ai_datagraph.shared_data.calculated_state

import cats.effect.{Async, Ref}
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.types.Types._
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.hash.Hash

import java.nio.charset.StandardCharsets

trait CalculatedStateService[F[_]] {
  def get: F[CalculatedState]

  def set(
    snapshotOrdinal: SnapshotOrdinal,
    state          : DatapointUpdateCalculatedState
  ): F[Boolean]

  def hash(
    state: DatapointUpdateCalculatedState
  ): F[Hash]
}

object CalculatedStateService {
  def make[F[_]: Async]: F[CalculatedStateService[F]] =
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {
        override def get: F[CalculatedState] = stateRef.get

        override def set(
          snapshotOrdinal: SnapshotOrdinal,
          state          : DatapointUpdateCalculatedState
        ): F[Boolean] = stateRef.modify { currentState =>
          val currentCalculatedState = currentState.state
          val updatedUpdates = state.updates.foldLeft(currentCalculatedState.updates) {
            case (acc: Map[Address, DatapointUpdate], (address, value)) =>
              acc.updated(address, value)
          }
          val newState = CalculatedState(snapshotOrdinal, DatapointUpdateCalculatedState(updatedUpdates))

          // Return the new state and the Boolean `true`
          newState -> true
        }  // No need to wrap the result of `modify`

        override def hash(
          state: DatapointUpdateCalculatedState
        ): F[Hash] = {
          def removeField(json: Json, fieldName: String): Json = {
            json.mapObject(_.filterKeys(_ != fieldName).mapValues(removeField(_, fieldName))).mapArray(_.map(removeField(_, fieldName)))
          }

          val jsonState = removeField(state.asJson, "postTime").deepDropNullValues.noSpaces
          Hash.fromBytes(jsonState.getBytes(StandardCharsets.UTF_8)).pure[F]
        }
      }
    }
}
