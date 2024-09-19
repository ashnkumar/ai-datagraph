package com.my.shared_data

import cats.effect.Async
import cats.syntax.all._
import org.tessellation.security.Hasher

object Utils {

  def toTokenFormat(amount: Long): Long = amount * 1e8.toLong

  def hashData[F[_]: Async: Hasher](data: Map[String, String]): F[String] =
    Hasher[F].hash(data).map(_.value)
}
