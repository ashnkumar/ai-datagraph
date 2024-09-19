package com.my.shared_data.schema

import org.tessellation.currency.dataApplication.DataOnChainState
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

import com.my.shared_data.schema.Updates.{DataUpdateOnChain, DataUpdateRaw}

@derive(decoder, encoder)
case class OnChainState(
  dataUpdates: Map[String, DataUpdateOnChain] // dataHash -> DataUpdateOnChain
) extends DataOnChainState

object OnChainState {
  val genesis: OnChainState = OnChainState(Map.empty)
}