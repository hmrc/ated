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
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import reactivemongo.api.DB
import uk.gov.hmrc.mongo.{Awaiting, MongoSpecSupport}

class DisposeLiabilityReturnMongoRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with MongoSpecSupport
  with Awaiting
  with MockitoSugar
  with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    await(repository.drop)
  }

  def repository(implicit mongo: () => DB) = new DisposeLiabilityReturnReactiveMongoRepository()

  "DisposeLiabilityReturnRepository" must {

    lazy val formBundle1 = ChangeLiabilityReturnBuilder.generateFormBundleResponse(2015)
    lazy val formBundle2 = ChangeLiabilityReturnBuilder.generateFormBundleResponse(2015)

    lazy val disposeLiability1 = DisposeLiabilityReturn("ated-ref-123", "123456789012", formBundle1)
    lazy val disposeLiability2 = DisposeLiabilityReturn("ated-ref-456", "123456789013", formBundle2)

    "cacheDisposeLiabilityReturns" must {

      "cache DisposeLiabilityReturn into mongo" in {
        await(repository.cacheDisposeLiabilityReturns(disposeLiability1))
      }

      "does not save the next disposeLiabilityReturns into mongo, if same id are passed for next document" in {
        await(repository.cacheDisposeLiabilityReturns(disposeLiability1))
        await(repository.cacheDisposeLiabilityReturns(disposeLiability2.copy(id = "123456789012")))
        await(repository.fetchDisposeLiabilityReturns("ated-ref-123")).isEmpty must be(false)
      }
    }

    "fetchDisposeLiabilityReturns" must {
      "if data doesn't exist, should return empty list" in {
        await(repository.fetchDisposeLiabilityReturns("ated-ref-123")).isEmpty must be(true)
      }
      "if data does exist, should return that in list" in {
        await(repository.cacheDisposeLiabilityReturns(disposeLiability1))
        await(repository.cacheDisposeLiabilityReturns(disposeLiability2))
        await(repository.fetchDisposeLiabilityReturns("ated-ref-123")).isEmpty must be(false)
        await(repository.fetchDisposeLiabilityReturns("ated-ref-456")).isEmpty must be(false)
      }
    }

    "deleteDisposeLiabilityReturns" must {
      "delete data from mongo" in {

        await(repository.cacheDisposeLiabilityReturns(disposeLiability1))
        await(repository.cacheDisposeLiabilityReturns(disposeLiability2))

        await(repository.fetchDisposeLiabilityReturns("ated-ref-123")).isEmpty must be(false)
        await(repository.fetchDisposeLiabilityReturns("ated-ref-456")).isEmpty must be(false)


        await(repository.deleteDisposeLiabilityReturns("ated-ref-123"))

        await(repository.fetchDisposeLiabilityReturns("ated-ref-123")).isEmpty must be(true)
        await(repository.fetchDisposeLiabilityReturns("ated-ref-456")).isEmpty must be(false)

      }
    }

  }

}
