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

import crypto.MongoCryptoProvider

import javax.inject.{Inject, Singleton}
import metrics.{MetricsEnum, ServiceMetrics}
import models.PropertyDetails

import java.time.{ZoneId, ZonedDateTime}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions}
import play.api.Logging
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.*
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mdc.Mdc.preservingMdc
import models.mongo.MongoDateTimeFormats

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
  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete]
  def metrics: ServiceMetrics
}

@Singleton
class PropertyDetailsMongoWrapperImpl @Inject()(val mongo: MongoComponent,
                                                val serviceMetrics: ServiceMetrics, val mongoCrypto: MongoCryptoProvider)(using val ec:ExecutionContext) extends PropertyDetailsMongoWrapper

trait PropertyDetailsMongoWrapper {
  given ec: ExecutionContext
  val mongo: MongoComponent
  val serviceMetrics: ServiceMetrics
  val mongoCrypto: MongoCryptoProvider
  given compositeCrypto: (Encrypter & Decrypter) = mongoCrypto.crypto
  private lazy val propertyDetailsRepository = new PropertyDetailsReactiveMongoRepository(mongo, serviceMetrics)

  def apply(): PropertyDetailsMongoRepository = propertyDetailsRepository
}

class PropertyDetailsReactiveMongoRepository(mongo: MongoComponent, val metrics: ServiceMetrics)
                                            (using crypto: Encrypter with Decrypter, ec: ExecutionContext)
  extends PlayMongoRepository[PropertyDetails](
    collectionName = "propertyDetails",
    mongoComponent = mongo,
    domainFormat = PropertyDetails.formats,
    indexes = Seq(
      IndexModel(ascending("id"), IndexOptions().name("idIndex").unique(true).sparse(true)),
      IndexModel(ascending("id", "periodKey", "atedRefNo"), IndexOptions().name("idAndperiodKeyAndAtedRefIndex").unique(true)),
      IndexModel(ascending("atedRefNo"), IndexOptions().name("atedRefIndex")),
      IndexModel(ascending("timestamp"), IndexOptions().name("propDetailsDraftExpiry").expireAfter(60 * 60 * 24 * 28, TimeUnit.SECONDS).sparse(true).background(true))
    ),
    extraCodecs = Seq(Codecs.playFormatCodec(MongoDateTimeFormats.tolerantDateTimeFormat))
  ) with PropertyDetailsMongoRepository with Logging {

  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertPropDetails)
    val query = and(equal("periodKey", propertyDetails.periodKey), equal("atedRefNo", propertyDetails.atedRefNo), equal("id", propertyDetails.id))
    val propertyDetailsTimestampUpdate = propertyDetails.copy(timeStamp = ZonedDateTime.now(ZoneId.of("UTC")))
    val replaceOptions = ReplaceOptions().upsert(true)

    preservingMdc(
      collection.replaceOne(query, propertyDetailsTimestampUpdate, replaceOptions).toFutureOption().map {
        case Some(writeResult) =>
          timerContext.stop()
          if (writeResult.wasAcknowledged() && writeResult.getModifiedCount == 1) {
            PropertyDetailsCached
          } else {
            PropertyDetailsCacheError
          }
        case None =>
          logger.warn("Failed to update or insert property details, no WriteResult")
          timerContext.stop()
          PropertyDetailsCacheError
        } recover {
          case e => logger.warn("Failed to update or insert property details", e)
            timerContext.stop()
            PropertyDetailsCacheError
        }
    )
  }

  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = equal("atedRefNo", atedRefNo)

    val result: Future[Option[Seq[PropertyDetails]]] = preservingMdc {
      collection.find(query).collect().toFutureOption()
    }

    result onComplete {
      _ => timerContext.stop()
    }

    result map { _.toSeq.flatten}
  }

  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = and(equal("atedRefNo", atedRefNo), equal("id", id))

    val result: Future[Option[Seq[PropertyDetails]]] = preservingMdc {
      collection.find(query).collect().toFutureOption()
    }

    result onComplete {
      _ => timerContext.stop()
    }

    result map { _.toSeq.flatten}
  }

  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetailsByFieldName)
    val query = and(equal("atedRefNo", atedRefNo), equal("id", id))

    preservingMdc(collection.deleteOne(query).toFutureOption().map {
      case Some(removeResult) =>
        if (removeResult.wasAcknowledged() && removeResult.getDeletedCount == 1) {
          PropertyDetailsDeleted
        } else {
          PropertyDetailsDeleteError
        }
      case None => logger.warn("Failed to remove property details, no RemoveResult")
        timerContext.stop()
        PropertyDetailsDeleteError
    } recover {
      case e => logger.warn("Failed to remove property details", e)
        timerContext.stop()
        PropertyDetailsDeleteError
    })
  }
}
