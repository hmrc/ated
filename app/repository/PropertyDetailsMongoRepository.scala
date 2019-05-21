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

package repository

import java.util.concurrent.TimeUnit
import metrics.{Metrics, MetricsEnum}
import models.{CalculatedPeriod, PropertyDetails, PropertyDetailsAddress, PropertyDetailsCalculated, PropertyDetailsPeriod, PropertyDetailsTitle, PropertyDetailsValue}
import mongo.{MongoCollection2, ReactiveRepository, CodecProviders}
import mongo.json.ReactiveMongoFormats
import org.joda.time.{DateTime, DateTimeZone}
import org.bson.codecs.configuration.CodecRegistries
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.mongodb.scala.model.{Filters, Indexes, IndexModel, IndexOptions, Updates, UpdateOptions, FindOneAndReplaceOptions}
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.codecs.Macros
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

sealed trait PropertyDetailsCache
case object PropertyDetailsCached extends PropertyDetailsCache
case object PropertyDetailsCacheError extends PropertyDetailsCache

sealed trait PropertyDetailsDelete
case object PropertyDetailsDeleted extends PropertyDetailsDelete
case object PropertyDetailsDeleteError extends PropertyDetailsDelete

trait PropertyDetailsMongoRepository {

  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache]

  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]]

  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]]

  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete]

  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete]

  def drop: Future[Unit]
}

object PropertyDetailsMongoRepository {
  private lazy val propertyDetailsMongoRepository = new PropertyDetailsReactiveMongoRepository
  def apply(): PropertyDetailsMongoRepository = propertyDetailsMongoRepository
}

class PropertyDetailsReactiveMongoRepository
    extends PropertyDetailsMongoRepository
       with WithTimer {

  val collection: MongoCollection[PropertyDetails] =
    MongoCollection2.collection("propertyDetails",
      CodecRegistries.fromProviders(
          Macros.createCodecProvider[PropertyDetails]()
        , Macros.createCodecProvider[PropertyDetailsAddress]()
        , Macros.createCodecProvider[PropertyDetailsTitle]()
        , Macros.createCodecProvider[PropertyDetailsValue]()
        , Macros.createCodecProvider[PropertyDetailsPeriod]()
        , Macros.createCodecProvider[PropertyDetailsCalculated]()
        , Macros.createCodecProvider[CalculatedPeriod]()
        , CodecProviders.bigDecimalCodecProvider
        , CodecProviders.localDateCodecProvider
        , CodecProviders.dateTimeCodecProvider
        ))

  Await.result(collection
    .createIndexes(Seq(
        IndexModel( Indexes.ascending("id")
                  , IndexOptions().name("idIndex").unique(true).sparse(true)
                  )
      , IndexModel( Indexes.ascending("id", "periodKey", "atedRefNo")
                  , IndexOptions().name("idAndperiodKeyAndAtedRefIndex").unique(true)
                  )
      , IndexModel( Indexes.ascending("atedRefNo")
                  , IndexOptions().name("atedRefIndex")
                  )
      , IndexModel( Indexes.ascending("timestamp")
                  , IndexOptions().name("propDetailsDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true)
                  )
      )).toFuture, 1.seconds)


  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache] =
    withTimer(MetricsEnum.RepositoryInsertDispLiability){
    collection
      .findOneAndReplace(
          filter      = Document(
                            "periodKey" -> propertyDetails.periodKey
                          , "atedRefNo" -> propertyDetails.atedRefNo
                          , "id"        -> propertyDetails.id
                          )
        , replacement = propertyDetails
                          .copy(timeStamp = DateTime.now(DateTimeZone.UTC))
        , options = FindOneAndReplaceOptions().upsert(true)
        )
      .toFuture
      .map(_ => PropertyDetailsCached)
      .recover { case e  => Logger.warn("Failed to update or insert property details", e); PropertyDetailsCacheError }
    }

  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]] =
    withTimer(MetricsEnum.RepositoryFetchPropDetails){
      collection
        .find(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
    }

  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]] =
    withTimer(MetricsEnum.RepositoryFetchPropDetails){
      collection
        .find(filter = Document("atedRefNo" -> atedRefNo, "id" -> id))
        .toFuture
    }

  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete] =
    withTimer(MetricsEnum.RepositoryDeletePropDetails){
      collection
        .deleteMany(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
        .map(_.getDeletedCount match {
          case 1 => PropertyDetailsDeleted
          case _ => PropertyDetailsDeleteError
          })
        .recover {
          case e => Logger.warn("Failed to remove property details", e); PropertyDetailsDeleteError
      }
    }

  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete] =
    withTimer(MetricsEnum.RepositoryDeletePropDetails){
      collection
        .deleteOne(filter = Document("atedRefNo" -> atedRefNo, "id" -> id))
        .toFuture
        .map(_.getDeletedCount match {
          case 1 => PropertyDetailsDeleted
          case _ => PropertyDetailsDeleteError
          })
        .recover {
          case e => Logger.warn("Failed to remove property details", e); PropertyDetailsDeleteError
      }
    }

  def drop: Future[Unit] =
    collection
      .drop()
      .toFuture
      .map(_ => ())
}
