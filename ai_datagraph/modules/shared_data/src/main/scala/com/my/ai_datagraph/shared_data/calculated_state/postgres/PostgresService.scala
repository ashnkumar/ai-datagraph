package com.my.ai_datagraph.shared_data.postgres

import cats.effect._
import cats.syntax.all._
import com.my.ai_datagraph.shared_data.types.Types.Datapoint
import doobie.hikari.HikariTransactor
import doobie.implicits._
import org.tessellation.schema.address.Address
import scala.concurrent.ExecutionContext

object PostgresService {
  def make[F[_] : Async](url: String, user: String, password: String): Resource[F, PostgresService[F]] = {

    val transactor = for {
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver", url, user, password, ExecutionContext.global
      )
    } yield xa

    transactor.map { xa =>
      new PostgresService[F](xa)
    }
  }
}

class PostgresService[F[_] : Async](xa: HikariTransactor[F]) {

  // Insert datapoint into the data_updates table
  def insertDatapointUpdate(
    hash: String,
    privateData: Datapoint,
    timestamp: Long,
    address: Address
  ): F[Int] = {
    // Use the `data_updates` table for both on-chain and private data
    sql"""
      INSERT INTO data_updates (hash, data, timestamp, address)
      VALUES ($hash, $privateData::jsonb, $timestamp, ${address.value})
      ON CONFLICT (hash) DO UPDATE SET data = EXCLUDED.data
    """.update.run.transact(xa)
  }

  // Additional queries can be added as needed
}
