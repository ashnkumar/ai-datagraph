package com.my.water_and_energy_usage.shared_data.calculated_state

import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.all._
import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState
import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState.hash
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash

/**
 * Trait that defines the service for managing and updating the calculated state
 * of the application, including getting the current state, setting new states,
 * and calculating the hash of the state.
 */
trait CalculatedStateService[F[_]] {

  /**
   * Fetches the current calculated state.
   *
   * @return The current `CalculatedState`
   */
  def getCalculatedState: F[CalculatedState]

  /**
   * Updates the calculated state by merging the new state with the existing one.
   * 
   * @param snapshotOrdinal The ordinal of the snapshot where this state is applied
   * @param state The new calculated state to be merged
   * @return A boolean indicating whether the state was updated successfully
   */
  def setCalculatedState(
    snapshotOrdinal: SnapshotOrdinal,
    state          : DatapointUpdateCalculatedState
  ): F[Boolean]

  /**
   * Computes the hash of the calculated state, used to verify integrity.
   * 
   * @param state The calculated state to hash
   * @return The hash of the state
   */
  def hashCalculatedState(
    state: DatapointUpdateCalculatedState
  ): F[Hash]
}

object CalculatedStateService {

  /**
   * Creates an instance of the `CalculatedStateService` using a `Ref` to store
   * the state in memory.
   *
   * @return An `F`-wrapped `CalculatedStateService` instance
   */
  def make[F[_] : Async]: F[CalculatedStateService[F]] = {
    // Initialize the state with an empty calculated state
    Ref.of[F, CalculatedState](CalculatedState.empty).map { stateRef =>
      new CalculatedStateService[F] {

        // Fetches the current calculated state from the reference
        override def getCalculatedState: F[CalculatedState] = stateRef.get

        // Sets the new calculated state by merging the new devices with the current ones
        override def setCalculatedState(
          snapshotOrdinal: SnapshotOrdinal,
          state          : DatapointUpdateCalculatedState
        ): F[Boolean] = 
          stateRef.modify { currentState =>
            // Merge current devices with the new devices from the updated state
            val devices = currentState.state.devices ++ state.devices
            CalculatedState(snapshotOrdinal, DatapointUpdateCalculatedState(devices)) -> true
          }

        // Hashes the calculated state to verify integrity
        override def hashCalculatedState(
          state: DatapointUpdateCalculatedState
        ): F[Hash] = Async[F].delay {
          hash(state)
        }
      }
    }
  }
}
