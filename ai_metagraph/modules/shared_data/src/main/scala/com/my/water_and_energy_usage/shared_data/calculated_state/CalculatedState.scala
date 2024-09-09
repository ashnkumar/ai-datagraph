package com.my.water_and_energy_usage.shared_data.calculated_state

import com.my.water_and_energy_usage.shared_data.types.Types.DatapointUpdateCalculatedState
import org.tessellation.schema.SnapshotOrdinal

/**
 * Represents the state of the data application at a given snapshot.
 * 
 * @param ordinal The ordinal of the snapshot representing the state
 * @param state The calculated state, which holds the aggregated device data
 */
case class CalculatedState(ordinal: SnapshotOrdinal, state: DatapointUpdateCalculatedState)

object CalculatedState {

  /**
   * Returns an empty state with the minimum snapshot ordinal and an empty map for devices.
   * This is used to initialize the state when no updates have been processed yet.
   */
  def empty: CalculatedState =
    CalculatedState(
      SnapshotOrdinal.MinValue, // Starting at the minimum snapshot ordinal
      DatapointUpdateCalculatedState(Map.empty) // No devices present yet
    )
}
