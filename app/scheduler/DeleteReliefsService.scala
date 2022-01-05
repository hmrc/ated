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
import repository.{LockRepositoryProvider, ReliefsMongoRepository, ReliefsMongoWrapper}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultDeleteReliefsService @Inject()(val servicesConfig: ServicesConfig,
                                            val repository: ReliefsMongoWrapper,
                                            val environment: Environment,
                                            val lockRepositoryProvider: LockRepositoryProvider,
                                            val configuration: Configuration
                                            ) extends DeleteReliefsService {
  override val documentBatchSize: Int = servicesConfig.getInt("schedules.delete-reliefs-job.cleardown.batchSize")
  lazy val lockoutTimeout: Int = servicesConfig.getInt("schedules.delete-reliefs-job.lockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = "delete-reliefs-job-lock"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo: LockRepository = lockRepositoryProvider.repo
  }
}

trait DeleteReliefsService extends ScheduledService[Int] with Logging {
  lazy val repo: ReliefsMongoRepository = repository()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository: ReliefsMongoWrapper
  val lockKeeper: LockKeeper
  val documentBatchSize: Int

  private def deleteOldReliefs(): Future[Int] = {
    repo.deleteExpired60Reliefs(documentBatchSize)
  }

  def invoke()(implicit ec: ExecutionContext): Future[Int] = {
    lockKeeper.tryLock(deleteOldReliefs()) map {
      case Some(result) =>
        logger.info(s"[DeleteReliefsService] Deleted $result draft documents past the 28 day limit")
        result
      case None =>
        logger.warn(s"[DeleteReliefsService] Failed to acquire lock")
        0
    }
  }
}

