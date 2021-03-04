/*
 * Copyright 2021 HM Revenue & Customs
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

import metrics.{MetricsEnum, ServiceMetrics}
import models.ReliefsTaxAvoidance
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.logging.Mdc

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait ReliefCached
case object ReliefCached extends ReliefCached
case object ReliefCachedError extends ReliefCached

sealed trait ReliefDelete
case object ReliefDeleted extends ReliefDelete
case object ReliefDeletedError extends ReliefDelete

@Singleton
class ReliefsMongoWrapperImpl @Inject()(val mongo: ReactiveMongoComponent,
                                        val serviceMetrics: ServiceMetrics)(
  override implicit val ec: ExecutionContext) extends ReliefsMongoWrapper

trait ReliefsMongoWrapper {
  implicit val ec: ExecutionContext
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
  def deleteExpired60Reliefs(batchSize: Int): Future[Int]
  def metrics: ServiceMetrics
}

class ReliefsReactiveMongoRepository(mongo: () => DB, val metrics: ServiceMetrics)(implicit val ec: ExecutionContext)
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
    Mdc.preservingMdc(collection.update(ordered = false)
      .one(query, relief.copy(timeStamp = date), upsert = false, multi = false)) map { res =>
      if (res.ok && res.nModified != 0) {
        logger.info(s"[updateTimestamp] Updated timestamp for ${relief.atedRefNo} with $date")
        ReliefCached
      } else {
        logger.error(s"[updateTimeStamp: Relief] Mongo failed to update, problem occurred in collect - ex: $res")
        ReliefCachedError
      }
    }
  }

  def deleteExpired60Reliefs(batchSize: Int): Future[Int] = {
    implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
    val query = BSONDocument("timeStamp" -> Json.obj("$lte" -> DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(61)))

    val logOnError = Cursor.ContOnError[Seq[ReliefsTaxAvoidance]]((_, ex) =>
      logger.error(s"[getExpiredReliefs] Mongo failed, problem occurred in collect - ex: ${ex.getMessage}")
    )
    val foundReliefs = Mdc.preservingMdc(collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[ReliefsTaxAvoidance]()
      .collect[Seq](batchSize, logOnError)
    )

    foundReliefs flatMap { reliefs =>
      Future.sequence(reliefs map { relief =>
        Mdc.preservingMdc(collection.delete()
          .one(BSONDocument("atedRefNo" -> relief.atedRefNo, "periodKey" -> relief.periodKey))) map { res =>
          if (res.ok && res.n == 1) {
            logger.info(s"${relief.atedRefNo} - ${relief.periodKey}")
            1
          } else {
            logger.error(s"[deleteExpiredReliefs] Mongo failed to remove an outdated relief - ex: $res")
            0
          }
        }
      }
      ) map {
        _.sum
      }
    }
  }

  def cacheRelief(relief: ReliefsTaxAvoidance): Future[ReliefCached] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertRelief)
    val query = BSONDocument("periodKey" -> relief.periodKey, "atedRefNo" -> relief.atedRefNo)

    Mdc.preservingMdc(collection.update(ordered = false)
      .one(query, relief.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false))
      .map { writeResult =>
        timerContext.stop()
        if (writeResult.ok) {
          ReliefCached
        } else {
          ReliefCachedError
        }
      }.recover {

      case e => logger.warn("Failed to update or insert relief", e)
        timerContext.stop()
        ReliefCachedError
    }
  }

  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    val result: Future[Seq[ReliefsTaxAvoidance]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[ReliefsTaxAvoidance]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[ReliefsTaxAvoidance]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    Mdc.preservingMdc(collection.delete().one(query)).map { removeResult =>
      if (removeResult.ok) {
        ReliefDeleted
      } else {
        ReliefDeletedError
      }
    }.recover {

      case e => logger.warn("Failed to remove relief", e)
        timerContext.stop()
        ReliefDeletedError
    }
  }

  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteReliefByYear)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "periodKey" -> periodKey)

    Mdc.preservingMdc(collection.delete().one(query)).map { removeResult =>
      if (removeResult.ok) {
        ReliefDeleted
      } else {
        ReliefDeletedError
      }
    }.recover {
      case e => logger.warn("Failed to remove relief by year", e)
        timerContext.stop()
        ReliefDeletedError
    }
  }
}
