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


import builders.ReliefBuilder
import models.{Reliefs, TaxAvoidance}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import reactivemongo.api.DB
import uk.gov.hmrc.mongo.{Awaiting, MongoSpecSupport}

class ReliefsMongoRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with MongoSpecSupport
  with Awaiting
  with MockitoSugar
  with BeforeAndAfterEach {

  def repository(implicit mongo: () => DB) = new ReliefsReactiveMongoRepository

  override def beforeEach(): Unit = {
    await(repository.drop)
  }

  val atedRefNo1 = "atedRef123"
  val periodKey = 2015
  val relief1 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, periodKey, Reliefs(periodKey = periodKey, rentalBusiness = true), TaxAvoidance(rentalBusinessScheme = Some("scheme123")))
  val relief2 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, periodKey, Reliefs(periodKey = periodKey, employeeOccupation = true), TaxAvoidance(rentalBusinessScheme = Some("scheme123")))
  val relief3 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, 2016, Reliefs(periodKey = 2016, rentalBusiness = true, farmHouses = true), TaxAvoidance(farmHousesScheme = Some("scheme999")))

  "ReliefsMongoRepository" must {

    "save relief draft" should {
      "save a new relief and then fetch" in {
        await(repository.cacheRelief(relief1))
        await(repository.fetchReliefs(atedRefNo1)).size must be(1)
      }

      "overwrite old relief when saving new one and fetch" in {
        await(repository.cacheRelief(relief1))
        await(repository.cacheRelief(relief2))
        await(repository.fetchReliefs(atedRefNo1)).size must be(1)
        await(repository.fetchReliefs(atedRefNo1)).head.reliefs.rentalBusiness must be(false)
        await(repository.fetchReliefs(atedRefNo1)).head.reliefs.employeeOccupation must be(true)
      }
    }

    "delete a relief" in {
      await(repository.cacheRelief(relief1))
      await(repository.deleteReliefs(atedRefNo1))
      await(repository.fetchReliefs(atedRefNo1)).size must be(0)
    }

    "delete a relief by year" in {
      await(repository.cacheRelief(relief1))
      await(repository.cacheRelief(relief3))
      await(repository.fetchReliefs(atedRefNo1)).size must be(2)
      await(repository.deleteDraftReliefByYear(atedRefNo1, periodKey))
      await(repository.fetchReliefs(atedRefNo1)).size must be(1)
    }
  }

}
