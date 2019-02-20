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

import metrics.{Metrics, MetricsEnum}
import models.PropertyDetails
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait PropertyDetailsCache

case object PropertyDetailsCached extends PropertyDetailsCache

case object PropertyDetailsCacheError extends PropertyDetailsCache

sealed trait PropertyDetailsDelete

case object PropertyDetailsDeleted extends PropertyDetailsDelete

case object PropertyDetailsDeleteError extends PropertyDetailsDelete

trait PropertyDetailsMongoRepository extends Repository[PropertyDetails, BSONObjectID] {

  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache]

  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]]

  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]]

  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete]

  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete]

  def metrics: Metrics

}

object PropertyDetailsMongoRepository extends MongoDbConnection {

  // $COVERAGE-OFF$
  private lazy val propertyDetailsRepository = new PropertyDetailsReactiveMongoRepository
  // $COVERAGE-ON$

  def apply(): PropertyDetailsMongoRepository = propertyDetailsRepository

}

class PropertyDetailsReactiveMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[PropertyDetails, BSONObjectID]("propertyDetails", mongo, PropertyDetails.formats, ReactiveMongoFormats.objectIdFormats)
    with PropertyDetailsMongoRepository {

  val metrics: Metrics = Metrics

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("idAndperiodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("propDetailsDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }

  // $COVERAGE-OFF$
  def cachePropertyDetails(propertyDetails: PropertyDetails): Future[PropertyDetailsCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertPropDetails)
    val query = BSONDocument("periodKey" -> propertyDetails.periodKey, "atedRefNo" -> propertyDetails.atedRefNo, "id" -> propertyDetails.id)
    collection.update(query, propertyDetails.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => PropertyDetailsCached
        case _ => PropertyDetailsCacheError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to update or insert property details", e)
        timerContext.stop()
        PropertyDetailsCacheError
      // $COVERAGE-ON$
    }
  }

  // $COVERAGE-OFF$
  def fetchPropertyDetails(atedRefNo: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    val result = collection.find(query).cursor[PropertyDetails]().collect[Seq]()

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def fetchPropertyDetailsById(atedRefNo: String, id: String): Future[Seq[PropertyDetails]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchPropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)

    val result = collection.find(query).cursor[PropertyDetails]().collect[Seq]()

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def deletePropertyDetails(atedRefNo: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetails)
    val query = BSONDocument("atedRefNo" -> atedRefNo)
    collection.remove(query).map { removeResult =>
      removeResult.ok match {
        case true => PropertyDetailsDeleted
        case _ => PropertyDetailsDeleteError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to remove property details", e)
        timerContext.stop()
        PropertyDetailsDeleteError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def deletePropertyDetailsByfieldName(atedRefNo: String, id: String): Future[PropertyDetailsDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeletePropDetailsByFieldName)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "id" -> id)
    collection.remove(query).map { removeResult =>
      removeResult.ok match {
        case true => PropertyDetailsDeleted
        case _ => PropertyDetailsDeleteError
      }
    }.recover {
      case e => Logger.warn("Failed to remove property details", e)
        timerContext.stop()
        PropertyDetailsDeleteError
    }
  }
  // $COVERAGE-ON$


}
