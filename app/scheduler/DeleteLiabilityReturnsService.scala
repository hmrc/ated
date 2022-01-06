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

package scheduler

import org.joda.time.Duration
import play.api.{Configuration, Environment, Logging}
import repository.{DisposeLiabilityReturnMongoRepository, DisposeLiabilityReturnMongoWrapper, LockRepositoryProvider}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.Mdc

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultDeleteLiabilityReturnsService @Inject()(val servicesConfig: ServicesConfig,
                                            val repository: DisposeLiabilityReturnMongoWrapper,
                                            val environment: Environment,
                                            val lockRepositoryProvider: LockRepositoryProvider,
                                            val configuration: Configuration,
                                                     override implicit val ec: ExecutionContext
                                            ) extends DeleteLiabilityReturnsService {

  override val documentBatchSize: Int = servicesConfig.getInt("schedules.delete-liability-returns-job.cleardown.batchSize")
  lazy val lockoutTimeout: Int = servicesConfig.getInt("schedules.delete-liability-returns-job.lockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = "delete-liability-returns-job-lock"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo: LockRepository = lockRepositoryProvider.repo
  }
}

trait DeleteLiabilityReturnsService extends ScheduledService[Int] with Logging {

  implicit val ec: ExecutionContext
  lazy val repo: DisposeLiabilityReturnMongoRepository = repository()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository: DisposeLiabilityReturnMongoWrapper
  val lockKeeper: LockKeeper
  val documentBatchSize: Int

  private def deleteOldLiabilityReturns(): Future[Int] = {
    repo.deleteExpired60DayLiabilityReturns(documentBatchSize)
  }

  def invoke()(implicit ec: ExecutionContext): Future[Int] = {
    Mdc.preservingMdc(lockKeeper.tryLock(deleteOldLiabilityReturns())) map {
      case Some(result) =>
        logger.info(s"[DeleteLiabilityReturnsService] Deleted $result draft documents past the given day limit")
        result
      case None =>
        logger.warn(s"[DeleteLiabilityReturnsService] Failed to acquire lock")
        0
    }
  }
}
