
package com.my.ai_metagraph.shared_data.external_apis

import cats.effect.Async
import cats.effect.std.Env
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.syntax._
import ujson.Obj
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object FirestoreClient {
  
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("FirestoreClient")

  /**
   * Sends the private data to Firestore using a POST request.
   */
  def sendToFirestore[F[_]: Async: Env](
    update         : DatapointUpdate,
    dataTypesHash  : String,
    privateDataHash: String
  ): F[Unit] = {
    for {
      firestoreApiUrl <- getEnv[F]("FIRESTORE_API_URL") // Get Firestore API URL from environment
      projectId       <- getEnv[F]("FIRESTORE_PROJECT_ID") // Get Firestore project ID from environment
      collectionName  <- getEnv[F]("FIRESTORE_COLLECTION_NAME") // Collection to store the data (e.g., "data_updates")
      endpoint = s"$firestoreApiUrl/v1/projects/$projectId/databases/(default)/documents/$collectionName"
      
      headers = Map(
        "Content-Type" -> "application/json",
        "Authorization" -> s"Bearer ${getEnv[F]("FIRESTORE_AUTH_TOKEN")}" // Firestore token from environment
      )
      
      // Prepare the JSON payload to be sent to Firestore
      requestBody = Obj(
        "fields" -> Obj(
          "address" -> Obj("stringValue" -> update.address.toString),
          "timestamp" -> Obj("integerValue" -> update.datapoint.timestamp.toString),
          "dataTypesHash" -> Obj("stringValue" -> dataTypesHash),
          "privateDataHash" -> Obj("stringValue" -> privateDataHash),
          "privateData" -> Obj("mapValue" -> update.datapoint.data.asJson.noSpaces)
        )
      ).render()

      _ <- logger.info(s"Firestore request body: $requestBody")

      // Send the POST request to Firestore
      response = requests.post(
        url = endpoint,
        headers = headers,
        data = requestBody
      ).text()

      _ <- logger.info(s"Firestore API response: $response")

    } yield ()
  }
}