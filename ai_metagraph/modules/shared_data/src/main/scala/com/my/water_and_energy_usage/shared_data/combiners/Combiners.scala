package com.my.ai_metagraph.shared_data.combiners

import com.my.ai_metagraph.shared_data.Utils.getUpdateHash
import com.my.ai_metagraph.shared_data.types.Types._
import eu.timepit.refined.types.numeric.NonNegLong
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.Address
import org.tessellation.security.signature.Signed

object Combiners {

  /**
   * Get the updated list of datapoint transactions for a given device (address).
   * If the device already has a list of datapoints, append the new transaction.
   * 
   * @param datapoint The new datapoint update being processed (hash with all shared data)
   * @param acc The current state of the DataApplication (containing the state for all devices)
   * @param address The unique identifier (address) for the device
   * @return Updated list of DatapointTransaction for the device
   */
  private def getUpdatedDeviceDatapoints(
    datapoint: Datapoint,
    acc      : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    address  : Address
  ): List[DatapointTransaction] = {
    // Retrieve the current state of the device, or create an empty state if it doesn't exist yet
    val deviceCalculatedState = acc.calculated.devices.getOrElse(address, DeviceCalculatedState.empty)

    // Append the new DatapointTransaction to the device's existing list of transactions
    deviceCalculatedState.datapoints :+ DatapointTransaction(
      owner = address,
      datapoint = datapoint,
      lastTxnOrdinal = SnapshotOrdinal.MinValue,  // Placeholder, will be updated later
      lastTxnHash = ""                           // Placeholder, will be updated later
    )
  }

  /**
   * Combine the new datapoint update with the current state of the DataApplication.
   * This function will hash the update, update the state for the given device, and create a new transaction.
   * 
   * @param signedUpdate The new signed datapoint update (including all shared data in one update)
   * @param acc The current state of the DataApplication (on-chain and calculated)
   * @param lastSnapshotOrdinal The ordinal of the last snapshot (used to track transaction order)
   * @return Updated state of the DataApplication, including the new datapoint transaction
   */
  def combineDatapointUpdate(
    signedUpdate       : Signed[DatapointUpdate],
    acc                : DataState[DatapointUpdateState, DatapointUpdateCalculatedState],
    lastSnapshotOrdinal: SnapshotOrdinal
  ): DataState[DatapointUpdateState, DatapointUpdateCalculatedState] = {

    val update = signedUpdate.value
    val address = update.address

    // Generate a hash for the current update (to store the hash on-chain, not the private data)
    val updateHash = getUpdateHash(signedUpdate.value)

    // Get the last transaction reference for the device (or use an empty reference if no previous transactions)
    val lastTxnRef = acc.calculated.devices.get(address).fold(TxnRef.empty)(_.currentTxnRef)

    // Create a new DatapointTransaction for this update
    val datapointTransaction = DatapointTransaction(
      owner = address,
      datapoint = update.datapoint,
      lastTxnOrdinal = lastTxnRef.txnSnapshotOrdinal,  // Use the last known transaction's ordinal
      lastTxnHash = lastTxnRef.txnHash                 // Use the last known transaction's hash
    )

    // Get the updated list of DatapointTransactions for this device (appending the new one)
    val updatedDeviceDatapoints = getUpdatedDeviceDatapoints(update.datapoint, acc, address)

    // Calculate the new snapshot ordinal for this update (increment from the last one)
    val currentSnapshotOrdinal: SnapshotOrdinal = SnapshotOrdinal(NonNegLong.unsafeFrom(lastSnapshotOrdinal.value.value + 1))

    // Update the device's calculated state with the new transaction reference
    val device = DeviceCalculatedState(
      datapoints = updatedDeviceDatapoints,
      currentTxnRef = TxnRef(currentSnapshotOrdinal, updateHash)
    )

    // Update the map of devices with the updated device state
    val devices = acc.calculated.devices.updated(address, device)

    // Add the new transaction to the list of updates (signed with proofs)
    val updates: List[Signed[DatapointTransaction]] = Signed(datapointTransaction, signedUpdate.proofs) ::
      acc.onChain.updates.asInstanceOf[List[Signed[DatapointTransaction]]] // Ensure type consistency

    // Return the updated DataState, with both on-chain updates and calculated state for all devices
    DataState(
      DatapointUpdateState(updates.asInstanceOf[List[Signed[DatapointTransaction]]]), // On-chain updates
      DatapointUpdateCalculatedState(devices)                                         // Calculated state (per device)
    )
  }
}

