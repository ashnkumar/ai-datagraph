package com.my.shared_data.schema

import org.tessellation.currency.dataApplication.DataCalculatedState
import org.tessellation.schema.address.Address
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(decoder, encoder)
case class CalculatedState(
  rewardsToDistribute: Map[Address, Long], // Address -> Reward amount
  dataUsageTracking: Map[String, List[String]] // dataHash -> List of query IDs
) extends DataCalculatedState

object CalculatedState {
  val genesis: CalculatedState = CalculatedState(Map.empty, Map.empty)
}
