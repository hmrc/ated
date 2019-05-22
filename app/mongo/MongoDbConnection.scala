/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import play.api.{Configuration, Play}
import play.api.Mode.Mode

trait MongoDbConnection {
  private val configuration: Configuration = Play.current.configuration
  private val mode: play.api.Mode.Mode = Play.current.mode

  val mongoConfig = configuration
    .getConfig("mongodb")
    .orElse(configuration.getConfig(s"$mode.mongodb"))
    .orElse(configuration.getConfig(s"${play.api.Mode.Dev}.mongodb"))
    .getOrElse(sys.error("The application does not contain required mongodb configuration"))

  val uri = mongoConfig.getString("uri").getOrElse("mongodb.uri not defined")

  implicit val db = {
    val connectionString =
      new ConnectionString(uri)

    val mongoClientSettings =
      MongoClientSettings.builder
        .applyConnectionString(connectionString)
        .codecRegistry(DEFAULT_CODEC_REGISTRY)
        .build

    MongoClient(mongoClientSettings)
      .getDatabase(name = connectionString.getDatabase)
  }
}
