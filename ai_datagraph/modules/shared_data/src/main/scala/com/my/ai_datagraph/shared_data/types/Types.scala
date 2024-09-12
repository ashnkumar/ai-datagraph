package com.my.ai_datagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.Signed

object Types {

  /** Represents a raw data update received by the system. */
  @derive(decoder, encoder)
  case class DataUpdateRaw(
    address: Address,
    shared_data: Map[String, String]
  ) extends DataUpdate

  /** Represents a datapoint with associated metadata. */
  @derive(decoder, encoder)
  case class Datapoint(
    hash: String, // Now a String instead of custom Hash type
    timestamp: Long,
    sharedFields: List[String]
  )

  /** Represents a transformed datapoint update. */
  @derive(decoder, encoder)
  case class DatapointUpdate(
    address: Address,
    datapoint: Datapoint
  ) extends DataUpdate

  /** Represents a transaction involving a datapoint. */
  @derive(decoder, encoder)
  case class DatapointTransaction(
    owner: Address,
    datapoint: Datapoint,
    lastTxnOrdinal: SnapshotOrdinal,
    lastTxnHash: String
  ) extends DataUpdate

  /** Represents the on-chain state of datapoint updates. */
  @derive(decoder, encoder)
  case class DatapointUpdateState(
    updates: List[Signed[DatapointTransaction]]
  ) extends DataOnChainState

  /** Represents the calculated state based on datapoint updates. */
  @derive(decoder, encoder)
  case class DatapointUpdateCalculatedState(
    updates: Map[Address, DatapointUpdate]
  ) extends DataCalculatedState
}
