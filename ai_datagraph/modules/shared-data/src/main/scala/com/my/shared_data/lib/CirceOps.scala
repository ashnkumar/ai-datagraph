package com.my.shared_data.lib

import org.tessellation.currency.dataApplication.DataUpdate

import com.my.shared_data.schema.Updates.DataUpdateRaw

import io.circe._
import io.circe.syntax._

object CirceOps {

  object implicits {

    implicit val dataUpdateEncoder: Encoder[DataUpdate] = {
      case event: DataUpdateRaw => event.asJson
      case _                    => Json.Null
    }

    implicit val dataUpdateDecoder: Decoder[DataUpdate] = (c: HCursor) => c.as[DataUpdateRaw]
  }
}