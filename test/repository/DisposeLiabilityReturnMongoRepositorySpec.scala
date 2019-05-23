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

import builders.ChangeLiabilityReturnBuilder
import models.DisposeLiabilityReturn
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import org.scalatest.concurrent.ScalaFutures
import mongo.{MongoSpecSupport, RepositoryPreparation}
import scala.concurrent.ExecutionContext

class DisposeLiabilityReturnMongoRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with ScalaFutures
  with MongoSpecSupport
  with RepositoryPreparation
  with MockitoSugar
  with BeforeAndAfterEach {

  import ExecutionContext.Implicits.global

  lazy val repository = new DisposeLiabilityReturnReactiveMongoRepository()

  override implicit val patienceConfig = {
    import org.scalatest.time.{Millis, Span, Seconds}
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(15, Millis))
  }

  override def beforeEach(): Unit = {
    prepare(repository)
  }

  "DisposeLiabilityReturnRepository" must {

    lazy val formBundle1 = ChangeLiabilityReturnBuilder.generateFormBundleResponse(2015)
    lazy val formBundle2 = ChangeLiabilityReturnBuilder.generateFormBundleResponse(2015)

    lazy val disposeLiability1 = DisposeLiabilityReturn("ated-ref-123", "123456789012", formBundle1)
    lazy val disposeLiability2 = DisposeLiabilityReturn("ated-ref-456", "123456789013", formBundle2)

    "cacheDisposeLiabilityReturns" must {

      "cache DisposeLiabilityReturn into mongo" in {
        repository.cacheDisposeLiabilityReturns(disposeLiability1).futureValue
      }

      "not save the next disposeLiabilityReturns into mongo, if same id are passed for next document" in {
        repository.cacheDisposeLiabilityReturns(disposeLiability1).futureValue
        repository.cacheDisposeLiabilityReturns(disposeLiability2.copy(id = "123456789012")).futureValue
        repository.fetchDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue.isEmpty must be(false)
        repository.fetchDisposeLiabilityReturns(disposeLiability2.atedRefNo).futureValue.isEmpty must be(true)
      }
    }

    "fetchDisposeLiabilityReturns" must {
      "if data doesn't exist, should return empty list" in {
        repository.fetchDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue.isEmpty must be(true)
      }
      "if data does exist, should return that in list" in {
        repository.cacheDisposeLiabilityReturns(disposeLiability1).futureValue
        repository.cacheDisposeLiabilityReturns(disposeLiability2).futureValue
        repository.fetchDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue.isEmpty must be(false)
        repository.fetchDisposeLiabilityReturns(disposeLiability2.atedRefNo).futureValue.isEmpty must be(false)
      }
    }

    "deleteDisposeLiabilityReturns" must {
      "delete data from mongo" in {
        repository.cacheDisposeLiabilityReturns(disposeLiability1).futureValue
        repository.cacheDisposeLiabilityReturns(disposeLiability2).futureValue

        repository.fetchDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue.isEmpty must be(false)
        repository.fetchDisposeLiabilityReturns(disposeLiability2.atedRefNo).futureValue.isEmpty must be(false)

        repository.deleteDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue

        repository.fetchDisposeLiabilityReturns(disposeLiability1.atedRefNo).futureValue.isEmpty must be(true)
        repository.fetchDisposeLiabilityReturns(disposeLiability2.atedRefNo).futureValue.isEmpty must be(false)
      }
    }
  }
}
