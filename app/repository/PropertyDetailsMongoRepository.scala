/*
 * Copyright 2020 HM Revenue & Customs
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
import models.PropertyDetails
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{Format, Json, OFormat}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait PropertyDetailsCache
case object PropertyDetailsCached extends PropertyDetailsCache
case object PropertyDetailsCacheError extends PropertyDetailsCache

sealed trait PropertyDetailsDelete
case object PropertyDetailsDeleted extends PropertyDetailsDelete
case object PropertyDetailsDeleteError extends PropertyDetailsDelete

trait PropertyDetailsMongoRepository extends ReactiveRepository[PropertyDetails, BSONObjectID] {
  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache]
  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]]
  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]]
  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete]
  def deleteExpired60PropertyDetails(batchSize: Int): Future[Int]
  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete]
  def updateTimeStamp(propertyDetails: PropertyDetails, date: DateTime): Future[PropertyDetailsCache]
  def metrics: ServiceMetrics
}

@Singleton
class PropertyDetailsMongoWrapperImpl @Inject()(val mongo: ReactiveMongoComponent,
                                                val serviceMetrics: ServiceMetrics,
                                                val crypto: ApplicationCrypto) extends PropertyDetailsMongoWrapper

trait PropertyDetailsMongoWrapper {
  val mongo: ReactiveMongoComponent
  val serviceMetrics: ServiceMetrics
  val crypto: ApplicationCrypto
  implicit val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto

  private lazy val propertyDetailsRepository = new PropertyDetailsReactiveMongoRepository(mongo.mongoConnector.db, serviceMetrics)

  def apply(): PropertyDetailsMongoRepository = propertyDetailsRepository
}

class PropertyDetailsReactiveMongoRepository(mongo: () => DB, val metrics: ServiceMetrics)(implicit crypto: CompositeSymmetricCrypto)
  extends ReactiveRepository[PropertyDetails, BSONObjectID]("propertyDetails", mongo, PropertyDetails.formats, ReactiveMongoFormats.objectIdFormats)
    with PropertyDetailsMongoRepository {

  implicit val format: OFormat[PropertyDetails] = PropertyDetails.formats

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("idAndperiodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("propDetailsDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }

  def updateTimeStamp(propertyDetails: PropertyDetails, date: DateTime): Future[PropertyDetailsCache] = {
    val query = BSONDocument("periodKey" -> propertyDetails.periodKey, "atedRefNo" -> propertyDetails.atedRefNo, "id" -> propertyDetails.id)
    collection.update(ordered = false).one(query, propertyDetails.copy(timeStamp = date), upsert = false, multi = false) map { res =>
      if (res.ok && res.nModified != 0) {
        logger.info(s"[updateTimestamp] Updated timestamp for ${propertyDetails.id} with $date")
        PropertyDetailsCached
      } else {
        logger.error(s"[updateTimeStamp: PropertyDetails] Mongo failed to update, problem occurred in collect - ex: $res")
        PropertyDetailsCacheError
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
        collection.delete().one(BSONDocument("atedRefNo" -> propDetails.atedRefNo, "id" -> propDetails.id)) map { res =>
          if (res.ok && res.n == 1) {
            1
          } else {
            logger.error(s"[deleteExpiredLiabilityReturns] Mongo failed to remove an outdated property details - ex: $res")
            0
          }
        }
      }) map {_.sum}
    }
  }

  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertPropDetails)
    val query = BSONDocument("periodKey" -> propertyDetails.periodKey, "atedRefNo" -> propertyDetails.atedRefNo, "id" -> propertyDetails.id)

    collection.update(ordered = false).one(query, propertyDetails.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
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

    val result:Future[Seq[PropertyDetails]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[PropertyDetails]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[PropertyDetails]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }



  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)

    val result:Future[Seq[PropertyDetails]] = collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[PropertyDetails]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[PropertyDetails]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }



  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo)
    collection.delete().one(query).map { removeResult =>
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



  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetailsByFieldName)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)
    collection.delete().one(query).map { removeResult =>
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
