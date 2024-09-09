package com.my.ai_metagraph.shared_data.types

import com.my.ai_metagraph.shared_data.Utils.removeKeyFromJSON
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.syntax.EncoderOps
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState, DataUpdate}
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Types {

  // Represents the core data point (e.g., health data) with its timestamp and shared fields
  @derive(decoder, encoder)
  case class Datapoint(
    hash        : String,         // Hash of the actual private data (stored off-chain)
    timestamp   : Long,           // When the data was recorded
    sharedFields: List[String]    // List of fields that are shared and queryable (e.g., "age", "steps")
  )

  // Represents an update for a single datapoint from a specific address
  @derive(decoder, encoder)
  case class DatapointUpdate(
    address   : Address,          // Address of the provider who submitted the update
    datapoint : Datapoint         // The actual datapoint (with private data hash and shared fields)
  ) extends DataUpdate

  // Represents a transaction that encapsulates a DatapointUpdate with metadata
  @derive(decoder, encoder)
  case class DatapointTransaction(
    owner           : Address,         // Owner address of the update
    datapoint       : Datapoint,       // The actual datapoint (with hashed data and shared fields)
    lastTxnOrdinal  : SnapshotOrdinal, // Snapshot ordinal of the transaction
    lastTxnHash     : String           // Hash of the last transaction (for chaining)
  )

  // State representation on-chain, storing all datapoint updates
  @derive(decoder, encoder)
  case class DatapointUpdateState(
    updates: List[Signed[DatapointTransaction]] // List of signed transactions for audit and history
  ) extends DataOnChainState

  @derive(decoder, encoder)
  case class DeviceCalculatedState(
    datapoints   : List[DatapointTransaction],  // Aggregated transactions for the device
    currentTxnRef: TxnRef                       // Last transaction reference for this device
  )

  object DeviceCalculatedState {
    def empty: DeviceCalculatedState = DeviceCalculatedState(List.empty, TxnRef.empty)
  }

  // Calculated state on-chain (keeps track of devices and their transactions)
  @derive(decoder, encoder)
  case class DatapointUpdateCalculatedState(
    devices: Map[Address, DeviceCalculatedState]  // Map of Address to calculated state
  ) extends DataCalculatedState

  object DatapointUpdateCalculatedState {
    def hash(state: DatapointUpdateCalculatedState): Hash =
      Hash.fromBytes(
        removeKeyFromJSON(state.asJson, "timestamp")
          .deepDropNullValues
          .noSpaces
          .getBytes(StandardCharsets.UTF_8)
      )
  }

  @derive(decoder, encoder)
  case class TxnRef(
    txnSnapshotOrdinal: SnapshotOrdinal,
    txnHash           : String
  )

  object TxnRef {
    def empty: TxnRef = TxnRef(SnapshotOrdinal.MinValue, Hash.empty.value)
  }

  // Represents a group of transactions associated with an address, including the last transaction reference
  @derive(decoder, encoder)
  case class AddressTransactionsWithLastRef(
    txnRef: TxnRef, 
    txns  : List[DatapointTransaction]
  )
}
