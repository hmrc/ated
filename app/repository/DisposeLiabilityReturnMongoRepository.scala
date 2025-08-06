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
import models.DisposeLiabilityReturn
import java.time.{ZonedDateTime, ZoneId}
import org.mongodb.scala.model.Filters.{equal, _}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions, UpdateOptions}
import play.api.Logging
import uk.gov.hmrc.crypto.{ApplicationCrypto, Encrypter, Decrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mdc.Mdc.preservingMdc
import models.mongo.MongoDateTimeFormats
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCached extends DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCacheError extends DisposeLiabilityReturnCache

sealed trait DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleted extends DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleteError extends DisposeLiabilityReturnDelete

trait DisposeLiabilityReturnMongoRepository extends PlayMongoRepository[DisposeLiabilityReturn] {
  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache]
  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]]
  def updateTimeStamp(liabilityReturn: DisposeLiabilityReturn, date: ZonedDateTime): Future[DisposeLiabilityReturnDelete]
  def deleteExpired60DayLiabilityReturns(batchSize: Int): Future[Int]
  def metrics: ServiceMetrics
}

@Singleton
class DisposeLiabilityReturnMongoWrapperImpl @Inject()(val mongo: MongoComponent,
                                                       val serviceMetrics: ServiceMetrics,
                                                       val crypto: ApplicationCrypto)(
  override implicit val ec: ExecutionContext) extends DisposeLiabilityReturnMongoWrapper

trait DisposeLiabilityReturnMongoWrapper {
  implicit val ec: ExecutionContext
  val mongo: MongoComponent
  val serviceMetrics: ServiceMetrics
  val crypto: ApplicationCrypto
  implicit val compositeCrypto: Encrypter with Decrypter = crypto.JsonCrypto

  private lazy val disposeLiabilityReturnRepository = new DisposeLiabilityReturnRepository(mongo, serviceMetrics)

  def apply(): DisposeLiabilityReturnMongoRepository = disposeLiabilityReturnRepository
}

class DisposeLiabilityReturnRepository(mongo: MongoComponent, val metrics: ServiceMetrics)
                                                   (implicit crypto: Encrypter with Decrypter, ec: ExecutionContext)
  extends PlayMongoRepository[DisposeLiabilityReturn](
    collectionName = "disposeLiabilityReturns",
    mongoComponent = mongo,
    domainFormat = DisposeLiabilityReturn.mongoFormats,
    indexes = Seq(
      IndexModel(ascending("id"), IndexOptions().name("idIndex").unique(true).sparse(true)),
      IndexModel(ascending("id", "periodKey", "atedRefNo"), IndexOptions().name("idAndperiodKeyAndAtedRefIndex").unique(true)),
      IndexModel(ascending("atedRefNo"), IndexOptions().name("atedRefIndex")),
      IndexModel(ascending("timestamp"), IndexOptions().name("dispLiabilityDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true))
    ),
    extraCodecs = Seq(Codecs.playFormatCodec(MongoDateTimeFormats.tolerantDateTimeFormat))
  ) with DisposeLiabilityReturnMongoRepository with Logging {

  override def updateTimeStamp(liabilityReturn: DisposeLiabilityReturn, date: ZonedDateTime): Future[DisposeLiabilityReturnDelete] = {
    val query = and(equal("atedRefNo", liabilityReturn.atedRefNo), equal("id", liabilityReturn.id))
    val updateQuery = set("timeStamp", date)

    preservingMdc(collection.updateOne(query, updateQuery, UpdateOptions().upsert(false)).toFutureOption()) map {
      case Some(res) =>
        if (res.wasAcknowledged() && res.getModifiedCount == 1) {
          logger.info(s"[updateTimestamp] Updated timestamp for ${liabilityReturn.id} with $date")
          DisposeLiabilityReturnDeleted
        } else {
          logger.error(s"[updateTimeStamp: LiabilityReturn] Mongo failed to update, problem occurred in collect - ex: $res")
          DisposeLiabilityReturnDeleteError
        }
      case _ =>
        logger.error(s"[updateTimeStamp: LiabilityReturn] Mongo failed to update, problem occurred in collect, was not defined")
        DisposeLiabilityReturnDeleteError
    }
  }

  def deleteExpired60DayLiabilityReturns(batchSize: Int): Future[Int] = {
    val dayThreshold = 61
    val dateTimeThreshold = ZonedDateTime.now(ZoneId.of("UTC")).withHour(0).minusDays(dayThreshold)

    val query2 = lte("timeStamp", dateTimeThreshold)

    val foundLiabilityReturns: Future[Option[Seq[DisposeLiabilityReturn]]] = collection.find(query2).batchSize(batchSize).collect().toFutureOption()

    foundLiabilityReturns flatMap {
      case Some(res) =>
        Future.sequence(res map { rtn =>
          val deleteQuery = and(equal("atedRefNo", rtn.atedRefNo), equal("id", rtn.id))

          preservingMdc(collection.deleteOne(deleteQuery).toFutureOption()) map {
            case Some(res) =>
              if (res.wasAcknowledged() && res.getDeletedCount == 1) {
                1
              } else {
                logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to remove an outdated liability return - ex: $res")
                0
              }
            case None =>
              logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to remove an outdated liability return, no DeleteResult")
              0
          }
        }) map {
          _.sum
        }
      case None =>
        logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to delete outdated liability returns")
        Future.successful(0)
    }
  }

  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertDispLiability)
    val query = and(equal("atedRefNo", disposeLiabilityReturn.atedRefNo), equal("id", disposeLiabilityReturn.id))
    val disposeLiabilityReturnTimestampUpdate = disposeLiabilityReturn.copy(timeStamp = ZonedDateTime.now(ZoneId.of("UTC")))
    val replaceOptions = ReplaceOptions().upsert(true)

    preservingMdc(
      collection.replaceOne(query, disposeLiabilityReturnTimestampUpdate, replaceOptions).toFutureOption().map {
        case Some(writeResult) =>
          timerContext.stop()
          if (writeResult.wasAcknowledged() && writeResult.getModifiedCount == 1) {
            DisposeLiabilityReturnCached
          } else {
            DisposeLiabilityReturnCacheError
          }
        case None =>
          logger.warn("Failed to remove draft dispose liability")
          timerContext.stop()
          DisposeLiabilityReturnCacheError
        } recover {
          case e => logger.warn("Failed to remove draft dispose liability", e)
            timerContext.stop()
            DisposeLiabilityReturnCacheError
        }
    )
  }

  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchDispLiability)
    val query = equal("atedRefNo", atedRefNo)

    val result: Future[Option[Seq[DisposeLiabilityReturn]]] = preservingMdc {
      collection.find(query).collect().toFutureOption()
    }

    result onComplete {
      _ => timerContext.stop()
    }

    result map { _.toSeq.flatten}
  }
}
