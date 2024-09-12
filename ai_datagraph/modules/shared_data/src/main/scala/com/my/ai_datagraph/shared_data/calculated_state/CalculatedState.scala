package com.my.ai_datagraph.shared_data.calculated_state

import com.my.ai_datagraph.shared_data.types.Types.DatapointUpdateCalculatedState
import org.tessellation.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: DatapointUpdateCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(
      SnapshotOrdinal.MinValue,
      DatapointUpdateCalculatedState(Map.empty)
    )

}