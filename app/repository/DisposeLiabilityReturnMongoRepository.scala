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

import javax.inject.Inject
import metrics.{MetricsEnum, ServiceMetrics}
import models.DisposeLiabilityReturn
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.OFormat
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, CryptoWithKeysFromConfig}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCached extends DisposeLiabilityReturnCache
case object DisposeLiabilityReturnCacheError extends DisposeLiabilityReturnCache

sealed trait DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleted extends DisposeLiabilityReturnDelete
case object DisposeLiabilityReturnDeleteError extends DisposeLiabilityReturnDelete

trait DisposeLiabilityReturnMongoRepository extends ReactiveRepository[DisposeLiabilityReturn, BSONObjectID] {
  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache]
  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]]
  def deleteDisposeLiabilityReturns(atedRefNo: String): Future[DisposeLiabilityReturnDelete]
  def metrics: ServiceMetrics
}

class DisposeLiabilityReturnMongoWrapperImpl @Inject()(val mongo: ReactiveMongoComponent,
                                                       val serviceMetrics: ServiceMetrics,
                                                       val crypto: ApplicationCrypto) extends DisposeLiabilityReturnMongoWrapper

trait DisposeLiabilityReturnMongoWrapper {
  val mongo: ReactiveMongoComponent
  val serviceMetrics: ServiceMetrics
  val crypto: ApplicationCrypto
  implicit val compositeCrypto: CryptoWithKeysFromConfig = crypto.JsonCrypto

  private lazy val disposeLiabilityReturnRepository = new DisposeLiabilityReturnReactiveMongoRepository(mongo.mongoConnector.db, serviceMetrics)

  def apply(): DisposeLiabilityReturnMongoRepository = disposeLiabilityReturnRepository
}

class DisposeLiabilityReturnReactiveMongoRepository(mongo: () => DB, val metrics: ServiceMetrics)(implicit crypto: CompositeSymmetricCrypto)
  extends ReactiveRepository[DisposeLiabilityReturn, BSONObjectID]("disposeLiabilityReturns", mongo, DisposeLiabilityReturn.formats, ReactiveMongoFormats.objectIdFormats)
    with DisposeLiabilityReturnMongoRepository {

  implicit val format: OFormat[DisposeLiabilityReturn] = DisposeLiabilityReturn.formats

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "periodKey" -> IndexType.Ascending, "atedRefNo" -> IndexType.Ascending), name = Some("idAndperiodKeyAndAtedRefIndex"), unique = true),
      Index(Seq("atedRefNo" -> IndexType.Ascending), name = Some("atedRefIndex")),
      Index(Seq("timestamp" -> IndexType.Ascending), Some("dispLiabilityDraftExpiry"), options = BSONDocument("expireAfterSeconds" -> 60 * 60 * 24 * 28), sparse = true, background = true)
    )
  }


  def cacheDisposeLiabilityReturns(disposeLiabilityReturn: DisposeLiabilityReturn): Future[DisposeLiabilityReturnCache] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertDispLiability)
    val query = BSONDocument("atedRefNo" -> disposeLiabilityReturn.atedRefNo, "id" -> disposeLiabilityReturn.id)
    collection.update(query, disposeLiabilityReturn.copy(timeStamp = DateTime.now(DateTimeZone.UTC)), upsert = true, multi = false).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        DisposeLiabilityReturnCached
      } else {
        DisposeLiabilityReturnCacheError
      }
    }.recover {

      case e => Logger.warn("Failed to remove draft dispose liability", e)
        timerContext.stop()
        DisposeLiabilityReturnCacheError

    }
  }



  def fetchDisposeLiabilityReturns(atedRefNo: String): Future[Seq[DisposeLiabilityReturn]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchDispLiability)
    val query = BSONDocument("atedRefNo" -> atedRefNo)

    //TODO: Replace with find from ReactiveRepository
    val result:Future[Seq[DisposeLiabilityReturn]] = collection.find(query).cursor[DisposeLiabilityReturn]().collect[Seq](maxDocs = -1, err = FailOnError[Seq[DisposeLiabilityReturn]]())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }


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


}
