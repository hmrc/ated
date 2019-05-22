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

import builders.PropertyDetailsBuilder
import mongo.{MongoSpecSupport, RepositoryPreparation}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import scala.concurrent.ExecutionContext

class PropertyDetailsMongoRepositorySpec extends PlaySpec
  with OneServerPerSuite
  with ScalaFutures
  with MongoSpecSupport
  with RepositoryPreparation
  with MockitoSugar
  with BeforeAndAfterEach {

  import ExecutionContext.Implicits.global

  lazy val repository = new PropertyDetailsReactiveMongoRepository

  override def beforeEach(): Unit = {
    prepare(repository)
  }

  lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1")
  lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2")
  lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3")

  "PropertyDetailsMongoRepository" should {

    "Save stuff in mongo" should {

      "do it" in {
        val created = repository.cachePropertyDetails(propertyDetails1).futureValue
      }

      "overwrite old object" in {
        repository.cachePropertyDetails(propertyDetails1).futureValue
        repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(false)
      }
    }

    "retrieve stuff in mongo" should {

      "not find if something doesn't exist" in {
        repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(true)
      }
    }

    "fetch the correct property details" in {

      repository.cachePropertyDetails(propertyDetails1).futureValue
      repository.cachePropertyDetails(propertyDetails2).futureValue

      repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(false)
      repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(false)
    }

    "retrieve stuff in mongo by property id" should {

      "not find if something doesn't exist" in {
        repository.fetchPropertyDetailsById("ated-ref-1", "1").futureValue.isEmpty must be(true)
      }

      "fetch the correct property details" in {
        repository.cachePropertyDetails(propertyDetails1).futureValue

        repository.fetchPropertyDetailsById("ated-ref-1", "1").futureValue.isEmpty must be(false)
        repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(true)
      }
    }

    "delete stuff in mongo" should {

      "delete the correct property details" in {

        repository.cachePropertyDetails(propertyDetails1).futureValue
        repository.cachePropertyDetails(propertyDetails2).futureValue

        repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(false)
        repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(false)

        repository.deletePropertyDetails("ated-ref-1").futureValue

        repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(true)
        repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(false)
      }
    }

    "delete chargeable documents in mongo based on property id" in {

      repository.cachePropertyDetails(propertyDetails1).futureValue
      repository.cachePropertyDetails(propertyDetails2).futureValue

      repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(false)
      repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(false)

      repository.deletePropertyDetailsByfieldName("ated-ref-1", "1").futureValue
      repository.deletePropertyDetailsByfieldName("ated-ref-1", "2").futureValue

      repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(true)
      repository.fetchPropertyDetails("ated-ref-2").futureValue.isEmpty must be(false)
    }

    "update chargeable documents for an existing property id" in {

      repository.cachePropertyDetails(propertyDetails1).futureValue
      repository.cachePropertyDetails(propertyDetails2).futureValue

      repository.deletePropertyDetailsByfieldName("ated-ref-1", "1").futureValue

      repository.fetchPropertyDetails("ated-ref-1").futureValue.isEmpty must be(true)
    }

  }
}
