package com.my.ai_datagraph.shared_data

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.types.Types._
import io.circe.syntax._
import io.circe.Json
import org.tessellation.schema.address.Address
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.signature.SignatureProof

import java.nio.charset.StandardCharsets

object Utils {

  // Direct serialization of DataUpdateRaw using Circe without calling Serializers
  def getDataUpdateRawHash(
    update: DataUpdateRaw
  ): String = {
    // Serialize the DataUpdateRaw object to JSON and then to bytes
    val jsonString = update.asJson.deepDropNullValues.noSpaces
    val byteArray = jsonString.getBytes(StandardCharsets.UTF_8)

    // Generate a hash from the byte array
    Hash.fromBytes(byteArray).value
  }

  def getAllAddressesFromProofs[F[_] : Async : SecurityProvider](
    proofs: NonEmptySet[SignatureProof]
  ): F[List[Address]] =
    proofs
      .map(_.id)
      .toList
      .traverse(_.toAddress[F])

  def removeKeyFromJSON(json: Json, keyToRemove: String): Json =
    json.mapObject { obj =>
      obj.remove(keyToRemove).mapValues {
        case objValue: Json => removeKeyFromJSON(objValue, keyToRemove)
        case other => other
      }
    }.mapArray { arr =>
      arr.map(removeKeyFromJSON(_, keyToRemove))
    }
}
