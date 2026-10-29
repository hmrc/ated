/*
 * Copyright 2026 HM Revenue & Customs
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

import metrics.ServiceMetrics
import models.ReliefsTaxAvoidance
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

class ReliefsMongoRepositoryTest extends PlaySpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[ReliefsTaxAvoidance] {

  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val repository: ReliefsReactiveMongoRepository = new ReliefsReactiveMongoRepository(mongoComponent, metrics = mock[ServiceMetrics])

  "ChangeLiabilityReturnRepository" must {

    "have a ttl index defined" in {
      // automatically tested by DefaultPlayMongoRepositorySupport
    }
  }
}
