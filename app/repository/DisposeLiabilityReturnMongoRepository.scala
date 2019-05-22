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
import models.DisposeLiabilityReturn
import mongo.{MongoDbConnection, ReactiveRepository}
import mongo.playjson.CollectionFactory
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.mongodb.scala.model.{Indexes, IndexModel, IndexOptions, FindOneAndReplaceOptions}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

sealed trait DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCached extends DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCacheError extends DisposeLiabilityReturnCache

sealed trait DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleted extends DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleteError extends DisposeLiabilityReturnDelete

trait DisposeLiabilityReturnMongoRepository {

  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache]

  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]]

  def deleteDisposeLiabilityReturns(atedRefNo: String): Future[DisposeLiabilityReturnDelete]
}

object DisposeLiabilityReturnMongoRepository extends MongoDbConnection {
  private lazy val disposeLiabilityReturnMongoRepository = new DisposeLiabilityReturnReactiveMongoRepository
  def apply(): DisposeLiabilityReturnMongoRepository = disposeLiabilityReturnMongoRepository
}

class DisposeLiabilityReturnReactiveMongoRepository(implicit db: MongoDatabase)
  extends DisposeLiabilityReturnMongoRepository
     with ReactiveRepository[DisposeLiabilityReturn]
     with WithTimer {

  override val metrics = Metrics

  override val collection: MongoCollection[DisposeLiabilityReturn] =
    CollectionFactory.collection(db, "disposeLiabilityReturns", DisposeLiabilityReturn.formats)

  override val indices = Seq(
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
                , IndexOptions().name("dispLiabilityDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true)
                )
    )

  Await.result(collection.createIndexes(indices).toFuture, 1.seconds)

  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache] =
    withTimer(MetricsEnum.RepositoryInsertDispLiability){
      collection
        .findOneAndReplace(
            filter       = Document(
                              "atedRefNo" -> disposeLiabilityReturn.atedRefNo
                            , "id"        -> disposeLiabilityReturn.id
                            )
          , replacement = disposeLiabilityReturn
                            .copy(timeStamp = DateTime.now(DateTimeZone.UTC))
          , options     = FindOneAndReplaceOptions().upsert(true)
          )
        .toFuture
        .map(_ => DisposeLiabilityReturnCached)
        .recover { case e  => Logger.warn("Failed to cache dispose liability", e); DisposeLiabilityReturnCacheError }
    }

  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]] =
    withTimer(MetricsEnum.RepositoryFetchDispLiability){
      collection
        .find(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
    }

  def deleteDisposeLiabilityReturns(atedRefNo: String): Future[DisposeLiabilityReturnDelete] =
    withTimer(MetricsEnum.RepositoryDeleteDispLiability){
      collection
        .deleteOne(filter = Document("atedRefNo" -> atedRefNo))
        .toFuture
        .map(_.getDeletedCount match {
          case 1 => DisposeLiabilityReturnDeleted
          case _ => DisposeLiabilityReturnDeleteError
          })
        .recover {
          case e => Logger.warn("Failed to remove draft dispose liability", e); DisposeLiabilityReturnDeleteError
      }
    }
}

trait WithTimer {
  def metrics: Metrics
  def withTimer[R](metricsEnum: MetricsEnum.MetricsEnum)(f: => Future[R]): Future[R] = {
    val timerContext = metrics.startTimer(metricsEnum)
    val res = f
    res.onComplete(_ => timerContext.stop())
    res
  }
}
