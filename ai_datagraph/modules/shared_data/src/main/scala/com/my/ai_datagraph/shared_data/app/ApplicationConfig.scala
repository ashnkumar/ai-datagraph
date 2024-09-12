package com.my.ai_datagraph.shared_data.app

case class ApplicationConfig(
  postgresDatabase: ApplicationConfig.PostgresDatabase,
)

object ApplicationConfig {

  case class PostgresDatabase(
    url     : String,
    user    : String,
    password: String
  )
}
