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
import mongo.{MongoSpecSupport, RepositoryPreparation}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import scala.concurrent.ExecutionContext

class ReliefsMongoRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with ScalaFutures
  with MongoSpecSupport
  with RepositoryPreparation
  with MockitoSugar
  with BeforeAndAfterEach {

  import ExecutionContext.Implicits.global

  def repository = new ReliefsReactiveMongoRepository

  override def beforeEach(): Unit = {
    prepare(repository)
  }

  val atedRefNo1 = "atedRef123"
  val periodKey = 2015
  val relief1 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, periodKey, Reliefs(periodKey = periodKey, rentalBusiness = true), TaxAvoidance(rentalBusinessScheme = Some("scheme123")))
  val relief2 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, periodKey, Reliefs(periodKey = periodKey, employeeOccupation = true), TaxAvoidance(rentalBusinessScheme = Some("scheme123")))
  val relief3 = ReliefBuilder.reliefTaxAvoidance(atedRefNo1, 2016, Reliefs(periodKey = 2016, rentalBusiness = true, farmHouses = true), TaxAvoidance(farmHousesScheme = Some("scheme999")))

  "ReliefsMongoRepository" must {

    "save relief draft" should {
      "save a new relief and then fetch" in {
        repository.cacheRelief(relief1).futureValue
        repository.fetchReliefs(atedRefNo1).futureValue.size must be(1)
      }

      "overwrite old relief when saving new one and fetch" in {
        repository.cacheRelief(relief1).futureValue
        repository.cacheRelief(relief2).futureValue
        repository.fetchReliefs(atedRefNo1).futureValue.size must be(1)
        repository.fetchReliefs(atedRefNo1).futureValue.head.reliefs.rentalBusiness must be(false)
        repository.fetchReliefs(atedRefNo1).futureValue.head.reliefs.employeeOccupation must be(true)
      }
    }

    "delete a relief" in {
      repository.cacheRelief(relief1).futureValue
      repository.deleteReliefs(atedRefNo1).futureValue
      repository.fetchReliefs(atedRefNo1).futureValue.size must be(0)
    }

    "delete a relief by year" in {
      repository.cacheRelief(relief1).futureValue
      repository.cacheRelief(relief3).futureValue
      repository.fetchReliefs(atedRefNo1).futureValue.size must be(2)
      repository.deleteDraftReliefByYear(atedRefNo1, periodKey).futureValue
      repository.fetchReliefs(atedRefNo1).futureValue.size must be(1)
    }
  }

}
