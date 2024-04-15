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

package scheduler

import play.api.{Configuration, Environment, Logging}
import repository.{LockRepositoryProvider, ReliefsMongoRepository, ReliefsMongoWrapper}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.duration.{SECONDS, Duration}
import uk.gov.hmrc.mongo.lock.LockService

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
  val lockService: LockService = LockService(lockRepositoryProvider.repo, lockId = "delete-reliefs-job-lock", ttl = Duration.create(lockoutTimeout, SECONDS))
}

trait DeleteReliefsService extends ScheduledService[Int] with Logging {
  lazy val repo: ReliefsMongoRepository = repository()
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository: ReliefsMongoWrapper
  val lockService: LockService
  val documentBatchSize: Int

  private def deleteOldReliefs(): Future[Int] = {
    repo.deleteExpired60Reliefs(documentBatchSize)
  }

  def invoke()(implicit ec: ExecutionContext): Future[Int] = {
    lockService.withLock(deleteOldReliefs()) map {
      case Some(result) =>
        logger.info(s"[DeleteReliefsService] Deleted $result draft documents past the given day limit")
        result
      case None =>
        logger.warn(s"[DeleteReliefsService] Failed to acquire lock")
        0
    }
  }
}

