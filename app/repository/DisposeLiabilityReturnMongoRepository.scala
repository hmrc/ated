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
import models.DisposeLiabilityReturn
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

sealed trait DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCached extends DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCacheError extends DisposeLiabilityReturnCache

sealed trait DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleted extends DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleteError extends DisposeLiabilityReturnDelete

trait DisposeLiabilityReturnMongoRepository extends Repository[DisposeLiabilityReturn, BSONObjectID] {

  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache]

  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]]

  def deleteDisposeLiabilityReturns(atedRefNo: String): Future[DisposeLiabilityReturnDelete]

  def metrics: Metrics

}

object DisposeLiabilityReturnMongoRepository extends MongoDbConnection {
  // $COVERAGE-OFF$
  private lazy val disposeLiabilityReturnRepository = new DisposeLiabilityReturnReactiveMongoRepository
  // $COVERAGE-ON$
  def apply(): DisposeLiabilityReturnMongoRepository = disposeLiabilityReturnRepository

}

class DisposeLiabilityReturnReactiveMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[DisposeLiabilityReturn, BSONObjectID]("disposeLiabilityReturns", mongo, DisposeLiabilityReturn.formats, ReactiveMongoFormats.objectIdFormats)
    with DisposeLiabilityReturnMongoRepository {

  val metrics: Metrics = Metrics

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("idAndperiodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("dispLiabilityDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }

  // $COVERAGE-OFF$
  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertDispLiability)
    val query = BSONDocument("atedRefNo" -> disposeLiabilityReturn.atedRefNo, "id" -> disposeLiabilityReturn.id)
    collection.update(query, disposeLiabilityReturn.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => DisposeLiabilityReturnCached
        case _ => DisposeLiabilityReturnCacheError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to remove draft dispose liability", e)
        timerContext.stop()
        DisposeLiabilityReturnCacheError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchDispLiability)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    val result = collection.find(query).cursor[DisposeLiabilityReturn]().collect[Seq]()

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }
  // $COVERAGE-ON$
  // $COVERAGE-OFF$
  def deleteDisposeLiabilityReturns(atedRefNo: String): Future[DisposeLiabilityReturnDelete] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryDeleteDispLiability)
    val query = BSONDocument("atedRefNo" -> atedRefNo)
    collection.remove(query).map { removeResult =>
      removeResult.ok match {
        case true => DisposeLiabilityReturnDeleted
        case _ => DisposeLiabilityReturnDeleteError
      }
    }.recover {

      case e => Logger.warn("Failed to remove draft dispose liability", e)
        timerContext.stop()
        DisposeLiabilityReturnDeleteError

    }
  }
  // $COVERAGE-ON$

}
