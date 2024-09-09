package com.my.water_and_energy_usage.shared_data.deserializers

import com.my.water_and_energy_usage.shared_data.types.Types.{DatapointUpdate, DatapointUpdateCalculatedState, DatapointUpdateState}
import io.circe.Decoder
import io.circe.jawn.decode
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Deserializers {
  private def deserialize[A: Decoder](
    bytes: Array[Byte]
  ): Either[Throwable, A] =
    decode[A](new String(bytes, StandardCharsets.UTF_8))

  // Deserialize the DatapointUpdate from the byte array
  def deserializeUpdate(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdate] =
    deserialize[DatapointUpdate](bytes)

  // Deserialize the on-chain state from the byte array
  def deserializeState(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdateState] =
    deserialize[DatapointUpdateState](bytes)

  // Deserialize the DataApplicationBlock
  def deserializeBlock(
    bytes: Array[Byte]
  )(implicit e: Decoder[DataUpdate]): Either[Throwable, Signed[DataApplicationBlock]] =
    deserialize[Signed[DataApplicationBlock]](bytes)

  // Deserialize the calculated state from the byte array
  def deserializeCalculatedState(
    bytes: Array[Byte]
  ): Either[Throwable, DatapointUpdateCalculatedState] =
    deserialize[DatapointUpdateCalculatedState](bytes)
}
