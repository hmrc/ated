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
import models.ReliefsTaxAvoidance
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

sealed trait ReliefCached
case object ReliefCached extends ReliefCached
case object ReliefCachedError extends ReliefCached

sealed trait ReliefDelete
case object ReliefDeleted extends ReliefDelete
case object ReliefDeletedError extends ReliefDelete

trait ReliefsMongoRepository extends Repository[ReliefsTaxAvoidance, BSONObjectID] {

  def cacheRelief(reliefs: ReliefsTaxAvoidance): Future[ReliefCached]

  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]]

  def deleteReliefs(atedRefNo: String): Future[ReliefDelete]

  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int): Future[ReliefDelete]

  def metrics: Metrics
}

object ReliefsMongoRepository extends MongoDbConnection {

  // $COVERAGE-OFF$
  private lazy val reliefsRepository = new ReliefsReactiveMongoRepository
  // $COVERAGE-ON$

  def apply(): ReliefsMongoRepository = reliefsRepository
}

class ReliefsReactiveMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ReliefsTaxAvoidance, BSONObjectID]("reliefs", mongo, ReliefsTaxAvoidance.formats, ReactiveMongoFormats.objectIdFormats)
    with ReliefsMongoRepository {

  val metrics: Metrics = Metrics

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("periodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("reliefDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }
  // $COVERAGE-OFF$
  def cacheRelief(relief: ReliefsTaxAvoidance): Future[ReliefCached] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertRelief)
    val query = BSONDocument("periodKey" -> relief.periodKey, "atedRefNo" -> relief.atedRefNo)
    collection.update(query, relief.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => ReliefCached
        case _ => ReliefCachedError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to update or insert relief", e)
        timerContext.stop()
        ReliefCachedError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def fetchReliefs(atedRefNo: String): Future[Seq[ReliefsTaxAvoidance]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)
    val result = collection.find(query).cursor[ReliefsTaxAvoidance]().collect[Seq]()
    result onComplete {
      _ => timerContext.stop()
    }
    result
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def deleteReliefs(atedRefNo: String): Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteRelief)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    collection.remove(query).map { removeResult =>
      removeResult.ok match {
        case true => ReliefDeleted
        case _ => ReliefDeletedError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to remove relief", e)
        timerContext.stop()
        ReliefDeletedError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def deleteDraftReliefByYear(atedRefNo: String, periodKey: Int):Future[ReliefDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteReliefByYear)
    val query = BSONDocument("atedRefNo" -> atedRefNo, "periodKey" -> periodKey)

    collection.remove(query).map { removeResult =>
      removeResult.ok match {
        case true => ReliefDeleted
        case _ => ReliefDeletedError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to remove relief by year", e)
        timerContext.stop()
        ReliefDeletedError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$

}
