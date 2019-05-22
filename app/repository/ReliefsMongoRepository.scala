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
import models.{DisposeLiabilityReturn, ReliefsTaxAvoidance, Reliefs, TaxAvoidance}
import mongo.{MongoCollection2, CodecProviders}
import mongo.json.ReactiveMongoFormats
import org.bson.codecs.configuration.CodecRegistries
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import org.mongodb.scala.model.{Filters, Indexes, IndexModel, IndexOptions, Updates, UpdateOptions, FindOneAndReplaceOptions}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.codecs.Macros
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

sealed trait ReliefCached
case object ReliefCached extends ReliefCached
case object ReliefCachedError extends ReliefCached

sealed trait ReliefDelete
case object ReliefDeleted extends ReliefDelete
case object ReliefDeletedError extends ReliefDelete

trait ReliefsMongoRepository  {

  def cacheRelief(reliefs: ReliefsTaxAvoidance): Future[ReliefCached]

  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]]

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete]

  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete]

  def drop: Future[Unit]
}

object ReliefsMongoRepository {
  private lazy val reliefsMongoRepository = new ReliefsReactiveMongoRepository
  def apply(): ReliefsMongoRepository = reliefsMongoRepository
}

class ReliefsReactiveMongoRepository
  extends ReliefsMongoRepository
     with WithTimer {

  val collection: MongoCollection[ReliefsTaxAvoidance] =
    MongoCollection2.collection("reliefs", ReliefsTaxAvoidance.formats)



  Await.result(collection
    .createIndexes(Seq(
        IndexModel( Indexes.ascending("id")
                  , IndexOptions().name("idIndex").unique(true).sparse(true)
                  )
      , IndexModel( Indexes.ascending("periodKey", "atedRefNo")
                  , IndexOptions().name("periodKeyAndAtedRefIndex").unique(true)
                  )
      , IndexModel( Indexes.ascending("atedRefNo")
                  , IndexOptions().name("atedRefIndex")
                  )
      , IndexModel( Indexes.ascending("timestamp")
                  , IndexOptions().name("reliefDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true)
                  )
      )).toFuture, 1.seconds)


  def cacheRelief(relief: ReliefsTaxAvoidance): Future[ReliefCached] =
    withTimer(MetricsEnum.RepositoryInsertRelief){
      collection
        .findOneAndReplace(
            filter      = Document(
                              "periodKey" -> relief.periodKey
                            , "atedRefNo" -> relief.atedRefNo
                            )
          , replacement = relief
                            .copy(timeStamp = DateTime.now(DateTimeZone.UTC))
          , options = FindOneAndReplaceOptions().upsert(true)
          )
        .toFuture
        .map(_ => ReliefCached)
        .recover { case e => Logger.warn("Failed to update or insert relief", e); ReliefCachedError }
    }


  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] =
    withTimer(MetricsEnum.RepositoryFetchRelief){
      collection
        .find(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
    }

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete] =
    withTimer(MetricsEnum.RepositoryDeletePropDetails){
      collection
        .deleteMany(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
        .map(_.getDeletedCount match {
          case 1 => ReliefDeleted
          case _ => ReliefDeletedError
          })
        .recover { case e => Logger.warn("Failed to remove relief", e); ReliefDeletedError }
    }


  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete] =
    withTimer(MetricsEnum.RepositoryDeletePropDetails){
      collection
        .deleteMany(filter = Document(
                                 "atedRefNo" -> atedRefNo
                               , "periodKey" -> periodKey
                               ))
        .toFuture
        .map(_.getDeletedCount match {
          case 1 => ReliefDeleted
          case _ => ReliefDeletedError
          })
        .recover { case e => Logger.warn("Failed to remove relief by year", e); ReliefDeletedError }
    }

  def drop: Future[Unit] =
    collection
      .drop()
      .toFuture
      .map(_ => ())
}
