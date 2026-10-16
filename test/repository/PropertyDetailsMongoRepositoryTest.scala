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

import crypto.MongoCryptoProvider
import metrics.ServiceMetrics
import models.PropertyDetails
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext


private class PropertyDetailsMongoRepositoryTest extends PlaySpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[PropertyDetails] {
  private val mongoCrypto: MongoCryptoProvider = app.injector.instanceOf[MongoCryptoProvider]

  given crypto: (Encrypter & Decrypter) = mongoCrypto.crypto
  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override protected val repository: PropertyDetailsReactiveMongoRepository = new PropertyDetailsReactiveMongoRepository(mongoComponent, mock[ServiceMetrics])

  "PropertyDetailsRepository" must {

    "have a ttl index defined" in {
      // automatically tested by DefaultPlayMongoRepositorySupport
    }
  }

}
