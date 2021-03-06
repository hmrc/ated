/*
 * Copyright 2021 HM Revenue & Customs
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
import repository.{LockRepositoryProvider, PropertyDetailsMongoRepository, PropertyDetailsMongoWrapper}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultDeletePropertyDetailsService @Inject()(val servicesConfig: ServicesConfig,
                                                    val repository: PropertyDetailsMongoWrapper,
                                                    val environment: Environment,
                                                    val lockRepositoryProvider: LockRepositoryProvider,
                                                    val configuration: Configuration
                                            ) extends DeletePropertyDetailsService {
  override val documentBatchSize: Int = servicesConfig.getInt("schedules.delete-property-details-job.cleardown.batchSize")
  lazy val lockoutTimeout: Int = servicesConfig.getInt("schedules.delete-property-details-job.lockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = "delete-property-details-job-lock"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo: LockRepository = lockRepositoryProvider.repo
  }
}

trait DeletePropertyDetailsService extends ScheduledService[Int] with Logging {
  lazy val repo: PropertyDetailsMongoRepository = repository()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository: PropertyDetailsMongoWrapper
  val lockKeeper: LockKeeper
  val documentBatchSize: Int

  private def deleteOldPropertyDetails(): Future[Int] = {
    repo.deleteExpired60PropertyDetails(documentBatchSize)
  }

  def invoke()(implicit ec: ExecutionContext): Future[Int] = {
    lockKeeper.tryLock(deleteOldPropertyDetails()) map {
      case Some(result) =>
        logger.info(s"[deleteOldPropertyDetails] Deleted $result draft documents past the given day limit")
        result
      case None =>
        logger.warn(s"[deleteOldPropertyDetails] Failed to acquire lock")
        0
    }
  }
}
