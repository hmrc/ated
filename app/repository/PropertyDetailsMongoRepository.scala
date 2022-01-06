/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import metrics.{MetricsEnum, ServiceMetrics}
import models.{DisposeLiabilityReturn, PropertyDetails}
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.model.Filters.{and, equal, lte}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import play.api.Logging
import play.api.libs.json.{Format, Json, OFormat}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.mongo.{MongoComponent, ReactiveRepository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc
import uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.Implicits._

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

sealed trait PropertyDetailsCache
case object PropertyDetailsCached extends PropertyDetailsCache
case object PropertyDetailsCacheError extends PropertyDetailsCache

sealed trait PropertyDetailsDelete
case object PropertyDetailsDeleted extends PropertyDetailsDelete
case object PropertyDetailsDeleteError extends PropertyDetailsDelete

trait PropertyDetailsMongoRepository extends PlayMongoRepository[PropertyDetails] {
  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache]
  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]]
  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]]
  def deleteExpired60PropertyDetails(batchSize: Int): Future[Int]
  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete]
  def updateTimeStamp(propertyDetails: PropertyDetails, date: DateTime): Future[PropertyDetailsCache]
  def metrics: ServiceMetrics
}

@Singleton
class PropertyDetailsMongoWrapperImpl @Inject()(val mongo: MongoComponent,
                                                val serviceMetrics: ServiceMetrics,
                                                val crypto: ApplicationCrypto)(implicit val ec:ExecutionContext) extends PropertyDetailsMongoWrapper

trait PropertyDetailsMongoWrapper {
  implicit val ec: ExecutionContext
  val mongo: MongoComponent
  val serviceMetrics: ServiceMetrics
  val crypto: ApplicationCrypto
  implicit val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto

  private lazy val propertyDetailsRepository = new PropertyDetailsReactiveMongoRepository(mongo, serviceMetrics)

  def apply(): PropertyDetailsMongoRepository = propertyDetailsRepository
}

class PropertyDetailsReactiveMongoRepository(mongo: MongoComponent, val metrics: ServiceMetrics)
                                            (implicit crypto: CompositeSymmetricCrypto, ec: ExecutionContext)
  extends PlayMongoRepository[PropertyDetails](
    collectionName = "propertyDetails",
    mongoComponent = mongo,
    domainFormat = PropertyDetails.formats,
    indexes = Seq(
      IndexModel(ascending("id"), IndexOptions().name("idIndex").unique(true).sparse(true)),
      IndexModel(ascending("id", "periodKey", "atedRefNo"), IndexOptions().name("idAndperiodKeyAndAtedRefIndex").unique(true)),
      IndexModel(ascending("atedRefNo"), IndexOptions().name("atedRefIndex")),
      IndexModel(ascending("timeStamp"), IndexOptions().name("propDetailsDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true))
    ))
    with PropertyDetailsMongoRepository with Logging {

  implicit val format: OFormat[PropertyDetails] = PropertyDetails.formats

  def updateTimeStamp(propertyDetails: PropertyDetails, date: DateTime): Future[PropertyDetailsCache] = {
    val query = and(equal("atedRefNo", propertyDetails.atedRefNo), equal("id", propertyDetails.id), equal("periodKey", propertyDetails.periodKey))
    val updateQuery = set("timeStamp", Codecs.toBson(date))

    preservingMdc(collection.updateOne(query, updateQuery, UpdateOptions().upsert(false)).toFuture()) map { res =>
      if (res.wasAcknowledged() && res.getModifiedCount == 1) {
        logger.info(s"[updateTimestamp] Updated timestamp for ${propertyDetails.id} with $date")
        PropertyDetailsCached
      } else {
        logger.error(s"[updateTimeStamp: PropertyDetails] Mongo failed to update, problem occurred in collect - ex: $res")
        PropertyDetailsCacheError
      }
    }
  }

  def deleteExpired60DayLiabilityReturns(batchSize: Int): Future[Int] = {
    val dayThreshold = 61
    val jodaDateTimeThreshold = DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(dayThreshold)

    val query2 = lte("timeStamp", Codecs.toBson(jodaDateTimeThreshold))

    val foundLiabilityReturns: Future[Seq[DisposeLiabilityReturn]] = collection.find(query2).batchSize(batchSize).toFuture()

    foundLiabilityReturns flatMap { returns =>
      Future.sequence(returns map { rtn =>
        val deleteQuery = and(equal("atedRefNo", rtn.atedRefNo), equal("id", rtn.id))

        preservingMdc(collection.deleteOne(deleteQuery).toFuture()) map { res =>
          if (res.wasAcknowledged() && res.getDeletedCount == 1) {
            1
          } else {
            logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to remove an outdated liability return - ex: $res")
            0
          }
        }
      }) map {
        _.sum
      }
    }
  }

  def deleteExpired60PropertyDetails(batchSize: Int): Future[Int] = {
    implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
    val query = BSONDocument("timeStamp" -> Json.obj("$lte" -> DateTime.now(DateTimeZone.UTC).withHourOfDay(0).minusDays(61)))
    val logOnError = Cursor.ContOnError[Seq[PropertyDetails]]((_, ex) =>
      logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed, problem occurred in collect - ex: ${ex.getMessage}")
    )
    val foundPropertyDetails: Future[Seq[PropertyDetails]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[PropertyDetails]()
      .collect[Seq](batchSize, logOnError)

    

    foundPropertyDetails flatMap { propertyDetails =>
      Future.sequence(propertyDetails map { propDetails =>
        preservingMdc(collection.delete()
          .one(BSONDocument("atedRefNo" -> propDetails.atedRefNo, "id" -> propDetails.id))) map { res =>
          if (res.ok && res.n == 1) {
            1
          } else {
            logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to remove an outdated property details - ex: $res")
            0
          }
        }

      }) map {
        _.sum
      }
    }
  }

  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertPropDetails)
    val query = BSONDocument("periodKey" -> propertyDetails.periodKey, "atedRefNo" -> propertyDetails.atedRefNo, "id" -> propertyDetails.id)

    preservingMdc(collection.update(ordered = false)
      .one(query, propertyDetails.copy(timeStamp = DateTime.now(DateTimeZone.UTC)),
        upsert = true, multi = false)).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        PropertyDetailsCached
      } else {
        PropertyDetailsCacheError
      }
    } recover {
      case e => logger.warn("Failed to update or insert property details", e)
        timerContext.stop()
        PropertyDetailsCacheError
    }
  }

  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    val result: Future[Seq[PropertyDetails]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[PropertyDetails]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[PropertyDetails]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)

    val result: Future[Seq[PropertyDetails]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[PropertyDetails]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[PropertyDetails]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetailsByFieldName)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)

    preservingMdc(collection.delete().one(query)).map { removeResult =>
      if (removeResult.ok) {
        PropertyDetailsDeleted
      } else {
        PropertyDetailsDeleteError
      }
    }.recover {
      case e => logger.warn("Failed to remove property details", e)
        timerContext.stop()
        PropertyDetailsDeleteError
    }
  }
}
