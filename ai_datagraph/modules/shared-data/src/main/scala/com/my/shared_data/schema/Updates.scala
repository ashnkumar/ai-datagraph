package com.my.shared_data.schema

import org.tessellation.currency.dataApplication.DataUpdate
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.security.hash.Hash

object Updates {

  @derive(decoder, encoder)
  case class PublicData(
    address: String,
    timestamp: Long,
    fields: List[String]
    // Removed privateDataHash from here
  )

  @derive(decoder, encoder)
  case class PrivateData(
    sensitiveInfo: Map[String, String]
  )

  @derive(decoder, encoder)
  case class DataUpdateRaw(
    publicData: PublicData,
    privateData: PrivateData
  ) extends DataUpdate

  @derive(decoder, encoder)
  case class DataUpdateOnChain(
    address: String,
    timestamp: Long,
    fields: List[String],
    privateDataHash: Hash
  ) extends DataUpdate
}
