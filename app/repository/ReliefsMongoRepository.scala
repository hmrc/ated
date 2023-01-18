/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.model.Filters.{and, equal, lte}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions, UpdateOptions}
import play.api.Logging
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait ReliefCached
case object ReliefCached extends ReliefCached
case object ReliefCachedError extends ReliefCached

sealed trait ReliefDelete
case object ReliefDeleted extends ReliefDelete
case object ReliefDeletedError extends ReliefDelete

@Singleton
class ReliefsMongoWrapperImpl @Inject()(val mongo: MongoComponent,
                                        val serviceMetrics: ServiceMetrics)(
  override implicit val ec: ExecutionContext) extends ReliefsMongoWrapper

trait ReliefsMongoWrapper {
  implicit val ec: ExecutionContext
  val mongo: MongoComponent
  val serviceMetrics: ServiceMetrics

  private lazy val reliefsRepository = new ReliefsReactiveMongoRepository(mongo, serviceMetrics)
  def apply(): ReliefsMongoRepository = reliefsRepository
}

trait ReliefsMongoRepository extends PlayMongoRepository[ReliefsTaxAvoidance] {
  def cacheRelief(reliefs: ReliefsTaxAvoidance): Future[ReliefCached]
  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]]
  def fetchReliefsByYear(atedRefNo: String, periodKey: Int): Future[Seq[ReliefsTaxAvoidance]]
  def deleteReliefs(atedRefNo: String): Future[ReliefDelete]
  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete]
  def updateTimeStamp(relief: ReliefsTaxAvoidance, date: DateTime): Future[ReliefCached]
  def deleteExpired60Reliefs(batchSize: Int): Future[Int]
  def metrics: ServiceMetrics
}

class ReliefsReactiveMongoRepository(mongo: MongoComponent, val metrics: ServiceMetrics)
                                    (implicit val ec: ExecutionContext)
  extends  PlayMongoRepository[ReliefsTaxAvoidance](
    collectionName = "reliefs",
    mongoComponent = mongo,
    domainFormat = ReliefsTaxAvoidance.mongoFormats,
    indexes = Seq(
      IndexModel(ascending("id"), IndexOptions().name("idIndex").unique(true).sparse(true)),
      IndexModel(ascending("periodKey", "atedRefNo"), IndexOptions().name("periodKeyAndAtedRefIndex").unique(true)),
      IndexModel(ascending("atedRefNo"), IndexOptions().name("atedRefIndex")),
      IndexModel(ascending("timestamp"), IndexOptions().name("reliefDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true))
    ),
    extraCodecs = Seq(Codecs.playFormatCodec(MongoJodaFormats.dateTimeFormat))
  ) with ReliefsMongoRepository with Logging {

  def updateTimeStamp(relief: ReliefsTaxAvoidance, date: DateTime): Future[ReliefCached] = {
    val query = and(equal("atedRefNo", relief.atedRefNo), equal("periodKey", relief.periodKey))
    val updateQuery = set("timeStamp", date)

    preservingMdc(collection.updateOne(query, updateQuery, UpdateOptions().upsert(false)).toFutureOption()) map {
      case Some(res) =>
        if (res.wasAcknowledged() && res.getModifiedCount == 1) {
          logger.info(s"[updateTimestamp] Updated timestamp for ${relief.atedRefNo} with $date")
          ReliefCached
        } else {
          logger.error(s"[updateTimeStamp: Relief] Mongo failed to update, problem occurred in collect - ex: $res")
          ReliefCachedError
        }
      case None =>
        logger.error(s"[updateTimeStamp: Relief] Mongo failed to update, no UpdateResult")
        ReliefCachedError
    }
  }

  def deleteExpired60Reliefs(batchSize: Int): Future[Int] = {
    val dayThreshold = 61
    val jodaDateTimeThreshold = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(dayThreshold)

    val query2 = lte("timeStamp", jodaDateTimeThreshold)

    val foundReliefs: Future[Option[Seq[ReliefsTaxAvoidance]]] = collection.find(query2).batchSize(batchSize).collect().toFutureOption()

    foundReliefs flatMap {
      case Some(reliefs) =>
        Future.sequence(reliefs map { relief =>
          val deleteQuery = and(equal("atedRefNo", relief.atedRefNo), equal("periodKey", relief.periodKey))

          preservingMdc(collection.deleteOne(deleteQuery).toFutureOption()) map {
            case Some(res) =>
              if (res.wasAcknowledged() && res.getDeletedCount == 1) {
                logger.info(s"${relief.atedRefNo} - ${relief.periodKey}")
                1
              } else {
                logger.error(s"[deleteExpiredReliefs] Mongo failed to remove an outdated relief - ex: $res")
                0
              }
            case None =>
              logger.error(s"[deleteExpiredReliefs] Mongo failed to remove an outdated relief, no DeleteResult")
              0
          }
        }
        ) map {
          _.sum
        }
      case None =>
        logger.error(s"[deleteExpiredReliefs] Mongo failed to return FindResults")
        Future.successful(0)
    }
  }

  def cacheRelief(relief: ReliefsTaxAvoidance): Future[ReliefCached] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertRelief)
    val query = and(equal("atedRefNo", relief.atedRefNo), equal("periodKey", relief.periodKey))
    val reliefTimestampUpdate = relief.copy(timeStamp = DateTime.now(DateTimeZone.UTC))
    val replaceOptions = ReplaceOptions().upsert(true)

    preservingMdc(
      collection.replaceOne(query, reliefTimestampUpdate, replaceOptions).toFutureOption().map {
        case Some(writeResult) =>
          timerContext.stop()
          if (writeResult.wasAcknowledged()) {
            ReliefCached
          } else {
            ReliefCachedError
          }
        case None =>
          logger.warn("Failed to update or insert relief, no WriteResult")
          timerContext.stop()
          ReliefCachedError
      } recover {
        case e =>
          logger.warn("Failed to update or insert relief", e)
          timerContext.stop()
          ReliefCachedError
      }
    )
  }

  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchRelief)
    val query = equal("atedRefNo", atedRefNo)

    val result: Future[Option[Seq[ReliefsTaxAvoidance]]] = preservingMdc {
      collection.find(query).collect().toFutureOption()
    }

    result onComplete {
      _ => timerContext.stop()
    }

    result map { _.toSeq.flatten}
  }

  def fetchReliefsByYear(atedRefNo: String, periodKey: Int): Future[Seq[ReliefsTaxAvoidance]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchRelief)
    val query = and(equal("atedRefNo", atedRefNo), equal("periodKey", periodKey))

    val result: Future[Option[Seq[ReliefsTaxAvoidance]]] = preservingMdc {
      collection.find(query).collect().toFutureOption()
    }

    result onComplete {
      _ => timerContext.stop()
    }

    result map { _.toSeq.flatten}
  }

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteRelief)
    val query = and(equal("atedRefNo", atedRefNo))

    preservingMdc(collection.deleteMany(query).toFutureOption()).map {
      case Some(removeResult) =>
        if (removeResult.wasAcknowledged()) {
          ReliefDeleted
        } else {
          ReliefDeletedError
        }
      case None =>
        logger.warn("Failed to remove relief, no RemoveResult")
        timerContext.stop()
        ReliefDeletedError
    }.recover {
      case e => logger.warn("Failed to remove relief", e)
        timerContext.stop()
        ReliefDeletedError
    }
  }

  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteReliefByYear)
    val query = and(equal("atedRefNo", atedRefNo), equal("periodKey", periodKey))

    preservingMdc(collection.deleteOne(query).toFutureOption()).map {
      case Some(removeResult) =>
        if (removeResult.wasAcknowledged()) {
          ReliefDeleted
        } else {
          ReliefDeletedError
        }
      case None =>
        logger.warn("Failed to remove relief by year, no RemoveResult")
        timerContext.stop()
        ReliefDeletedError
    }.recover {
      case e => logger.warn("Failed to remove relief by year", e)
        timerContext.stop()
        ReliefDeletedError
    }
  }
}
