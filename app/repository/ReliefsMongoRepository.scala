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

import javax.inject.Inject
import metrics.{MetricsEnum, ServiceMetrics}
import models.ReliefsTaxAvoidance
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.{Cursor, DB}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait ReliefCached
case object ReliefCached extends ReliefCached
case object ReliefCachedError extends ReliefCached

sealed trait ReliefDelete
case object ReliefDeleted extends ReliefDelete
case object ReliefDeletedError extends ReliefDelete

class ReliefsMongoWrapperImpl @Inject()(val mongo: ReactiveMongoComponent,
                                        val serviceMetrics: ServiceMetrics) extends ReliefsMongoWrapper

trait ReliefsMongoWrapper {
  val mongo: ReactiveMongoComponent
  val serviceMetrics: ServiceMetrics

  private lazy val reliefsRepository = new ReliefsReactiveMongoRepository(mongo.mongoConnector.db, serviceMetrics)
  def apply(): ReliefsMongoRepository = reliefsRepository
}

trait ReliefsMongoRepository extends ReactiveRepository[ReliefsTaxAvoidance, BSONObjectID] {
  def cacheRelief(reliefs: ReliefsTaxAvoidance): Future[ReliefCached]
  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]]
  def deleteReliefs(atedRefNo: String): Future[ReliefDelete]
  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete]
  def updateTimeStamp(relief: ReliefsTaxAvoidance, date: DateTime): Future[ReliefCached]
  def deleteExpiredReliefs(batchSize: Int): Future[Int]
  def metrics: ServiceMetrics
}

class ReliefsReactiveMongoRepository(mongo: () => DB, val metrics: ServiceMetrics)
  extends ReactiveRepository[ReliefsTaxAvoidance, BSONObjectID]("reliefs", mongo, ReliefsTaxAvoidance.formats, ReactiveMongoFormats.objectIdFormats)
    with ReliefsMongoRepository {

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("periodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("reliefDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }

  def updateTimeStamp(relief: ReliefsTaxAvoidance, date: DateTime): Future[ReliefCached] = {
    val query = BSONDocument("periodKey" -> relief.periodKey, "atedRefNo" -> relief.atedRefNo)
    collection.update(query, relief.copy(timeStamp = date), upsert = false, multi = false) map { res =>
      if (res.ok) {
        ReliefCached
      } else {
        Logger.error(s"[updateTimeStamp: Relief] Mongo failed to update, problem occurred in collect - ex: $res")
        ReliefCachedError
      }
    }
  }

  def deleteExpiredReliefs(batchSize: Int): Future[Int] = {
    implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
    val query = BSONDocument("timeStamp" -> Json.obj("$lt" -> DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(28)))
    val logOnError = Cursor.ContOnError[Seq[ReliefsTaxAvoidance]]((_, ex) =>
      Logger.error(s"[getExpiredReliefs] Mongo failed, problem occurred in collect - ex: ${ex.getMessage}")
    )
    val foundReliefs = collection.find(query)
      .cursor[ReliefsTaxAvoidance]()
      .collect[Seq](batchSize, logOnError)

    collection.remove(query) map {res =>
      if (res.ok) {
        ReliefCached
      } else {
        Logger.error(s"[getExpiredReliefs] Mongo failed to remove an outdated Relief - ex: $res")
        ReliefCachedError
      }
    }
    foundReliefs map (_.size)
  }


  def cacheRelief(relief: ReliefsTaxAvoidance): Future[ReliefCached] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertRelief)
    val query = BSONDocument("periodKey" -> relief.periodKey, "atedRefNo" -> relief.atedRefNo)
    collection.update(query, relief.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        ReliefCached
      } else {
        ReliefCachedError
      }
    }.recover {

      case e => Logger.warn("Failed to update or insert relief", e)
        timerContext.stop()
        ReliefCachedError

    }
  }

  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)
    //TODO: Replace with find from ReactiveRepository
    val result:Future[Seq[ReliefsTaxAvoidance]] = collection.find(query).cursor[ReliefsTaxAvoidance]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[ReliefsTaxAvoidance]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    collection.remove(query).map { removeResult =>
      if (removeResult.ok) {
        ReliefDeleted
      } else {
        ReliefDeletedError
      }
    }.recover {

      case e => Logger.warn("Failed to remove relief", e)
        timerContext.stop()
        ReliefDeletedError

    }
  }

  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int):Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteReliefByYear)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "periodKey" -> periodKey)

    collection.remove(query).map { removeResult =>
      if (removeResult.ok) {
        ReliefDeleted
      } else {
        ReliefDeletedError
      }
    }.recover {
      case e => Logger.warn("Failed to remove relief by year", e)
        timerContext.stop()
        ReliefDeletedError
    }
  }

}
