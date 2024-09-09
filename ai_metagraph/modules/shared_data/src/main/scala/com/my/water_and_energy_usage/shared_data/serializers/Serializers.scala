package com.my.water_and_energy_usage.shared_data.serializers

import com.my.water_and_energy_usage.shared_data.types.Types.{DatapointUpdate, DatapointUpdateCalculatedState, DatapointUpdateState}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Serializers {
  private def serialize[A: Encoder](
    serializableData: A
  ): Array[Byte] = {
    serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  // Serialize the DatapointUpdate
  def serializeUpdate(
    update: DatapointUpdate
  ): Array[Byte] =
    serialize[DatapointUpdate](update)

  // Serialize the on-chain state
  def serializeState(
    state: DatapointUpdateState
  ): Array[Byte] =
    serialize[DatapointUpdateState](state)

  // Serialize the DataApplicationBlock
  def serializeBlock(
    state: Signed[DataApplicationBlock]
  )(implicit e: Encoder[DataUpdate]): Array[Byte] =
    serialize[Signed[DataApplicationBlock]](state)

  // Serialize the calculated state (used to store device-specific calculated data)
  def serializeCalculatedState(
    state: DatapointUpdateCalculatedState
  ): Array[Byte] =
    serialize[DatapointUpdateCalculatedState](state)
}
