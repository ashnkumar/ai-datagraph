package com.my.shared_data.lib

import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.my.shared_data.schema.Updates.PrivateData
import doobie.hikari.HikariTransactor
import doobie.implicits._
import io.circe.syntax._
import io.circe.parser._
import org.tessellation.security.hash.Hash

import scala.concurrent.ExecutionContext

trait PostgresService[F[_]] {
  def savePrivateData(hash: String, privateData: PrivateData): F[Unit]
  def getPrivateData(hash: String): F[Option[PrivateData]]
}

object PostgresService {

  def make[F[_]: Async](url: String, user: String, password: String): Resource[F, PostgresService[F]] = {

    val xaResource: Resource[F, HikariTransactor[F]] = for {
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver",
        url,
        user,
        password,
        ExecutionContext.global
      )
      _ <- Resource.eval(initializeDatabase(xa))
    } yield xa

    xaResource.map { xa =>
      new PostgresService[F] {

        override def savePrivateData(hash: String, privateData: PrivateData): F[Unit] = {
          val jsonData = privateData.asJson.noSpaces
          sql"INSERT INTO private_data (hash, data) VALUES ($hash, $jsonData::jsonb) ON CONFLICT (hash) DO NOTHING"
            .update.run.transact(xa).void
        }

        override def getPrivateData(hash: String): F[Option[PrivateData]] = {
          sql"SELECT data FROM private_data WHERE hash = $hash"
            .query[String]
            .map(decode[PrivateData](_).getOrElse(throw new Exception("Decoding error")))
            .option
            .transact(xa)
        }
      }
    }
  }

  private def initializeDatabase[F[_]: Async](xa: HikariTransactor[F]): F[Unit] = {
    val createTable =
      sql"""
        CREATE TABLE IF NOT EXISTS private_data (
          hash VARCHAR PRIMARY KEY,
          data JSONB NOT NULL
        )
      """.update.run

    createTable.transact(xa).void
  }
}
