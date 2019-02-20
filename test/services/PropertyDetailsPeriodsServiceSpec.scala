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

package services

import java.util.UUID

import builders.PropertyDetailsBuilder
import connectors.{AuthConnector, EtmpReturnsConnector}
import models._
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import reactivemongo.api.commands.WriteResult
import repository.{PropertyDetailsCached, PropertyDetailsMongoRepository}
import uk.gov.hmrc.mongo.DatabaseUpdate

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

class PropertyDetailsPeriodsServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockPropertyDetailsCache = mock[PropertyDetailsMongoRepository]
  val mockWriteResult = mock[WriteResult]
  val mockDatabaseUpdate = mock[DatabaseUpdate[Cache]]
  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector = mock[AuthConnector]

  object TestPropertyDetailsService extends PropertyDetailsPeriodService {
    override val propertyDetailsCache = mockPropertyDetailsCache
    override val etmpConnector = mockEtmpConnector
    override val authConnector = mockAuthConnector
  }

  val accountRef = "ATED-123123"

  override def beforeEach(): Unit = {
    reset(mockPropertyDetailsCache)
    reset(mockAuthConnector)
    reset(mockEtmpConnector)
  }

  val jsonEtmpResponse =
    """
      |{
      |  "processingDate": "2001-12-17T09:30:47Z",
      |  "reliefReturnResponse": [
      |    {
      |      "reliefDescription": "Property rental businesses",
      |      "formBundleNumber": "012345678912"
      |    }
      |  ],
      |  "liabilityReturnResponse": [
      |    {
      |      "mode": "Post",
      |      "propertyKey": "0000000002",
      |      "liabilityAmount": "1234.12",
      |      "paymentReference": "aaaaaaaaaaaaaa",
      |      "formBundleNumber": "012345678912"
      |    },
      |    {
      |      "mode": "Pre-Calculation",
      |      "propertyKey": "0000000001",
      |      "liabilityAmount": "999.99"
      |    }
      |  ]
      |}
    """.stripMargin

  "PropertyDetailsService: Periods" must {
    "use the correct DataCacheconnector" in {
      PropertyDetailsService.propertyDetailsCache must be(PropertyDetailsMongoRepository())
    }
  }
  implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  val propertyDetails1 = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("something"))
  val propertyDetails2 = PropertyDetailsBuilder.getFullPropertyDetails("2", Some("something else"))
  val propertyDetails3 = PropertyDetailsBuilder.getFullPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999.99)))



  "Save property details Full Tax Period" must {

    "Saving existing property details full tax period value when we don't have it in an existing list" in {
      val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
      val testPropertyDetailsPeriod = IsFullTaxPeriod(updatedpropertyDetails4.period.flatMap(_.isFullPeriod).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result = TestPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        updatedpropertyDetails4.id,
        testPropertyDetailsPeriod)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details full tax period updates an existing list. Change value so clear future values and add the new period" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
      val isFullPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod.map(x => !x)).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result = TestPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id, isFullPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod must be (Some(isFullPeriod.isFullPeriod))
      newProp.get.period.get.isInRelief.isDefined must be (false)
      newProp.get.period.get.liabilityPeriods.size must be (1)
      newProp.get.period.get.reliefPeriods.isEmpty must be (true)

    }

    "Saving existing property details full tax period updates an existing list. Change value so clear future values no period to add" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val isFullPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod.map(x => !x)).getOrElse(false), None)

      val result = TestPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id, isFullPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod must be (Some(isFullPeriod.isFullPeriod))
      newProp.get.period.get.isInRelief.isDefined must be (false)
      newProp.get.period.get.liabilityPeriods.isEmpty must be (true)
      newProp.get.period.get.reliefPeriods.isEmpty must be (true)

    }

    "Saving existing property details full tax period updates an existing list. Dont change value" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable = PropertyDetailsDatesLiable(new LocalDate("1970-01-01"), new LocalDate("1970-01-01"))
      val isFullPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result = TestPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id,
        isFullPeriod
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.period.isDefined must be (true)
      newProp.get.calculated.isDefined must be(true)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod must be (Some(isFullPeriod.isFullPeriod))
      newProp.get.period.get.isInRelief.isDefined must be (true)
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)

    }
  }

  "Save property details In Relief" must {

    "Saving existing property details in relief value when we don't have it in an existing list" in {
      val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val inRelief = PropertyDetailsInRelief(updatedpropertyDetails4.period.flatMap(_.isInRelief))
      val result = TestPropertyDetailsService.cacheDraftInRelief(accountRef,
        updatedpropertyDetails4.id, inRelief)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details in relief updates an existing list. Dont clear future values" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val inRelief = PropertyDetailsInRelief(propertyDetails3.period.flatMap(_.isInRelief.map(x => !x)))
      val result = TestPropertyDetailsService.cacheDraftInRelief(accountRef,
        propertyDetails3.id, inRelief)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod.isDefined must be (true)
      newProp.get.period.get.isInRelief must be (inRelief.isInRelief)
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)
      newProp.get.period.get.reliefPeriods.isEmpty must be (false)
    }
  }

  "Save property details DatesLiable" must {

    "Saving existing property details DatesLiable value when we don't have it in an existing list" in {
      val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val liabilityPeriod = propertyDetails3.period.flatMap(_.liabilityPeriods.headOption)
      val updatedValue = PropertyDetailsDatesLiable(
        liabilityPeriod.map(_.startDate).getOrElse(new LocalDate("1970-01-01")),
        liabilityPeriod.map(_.endDate).getOrElse(new LocalDate("1970-01-01"))
      )
      val result = TestPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        updatedpropertyDetails4.id, updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details DatesLiable updates an existing list. Change value so clear future values" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsDatesLiable(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod.isDefined must be (true)
      newProp.get.period.get.isInRelief.isDefined must be (true)
      newProp.get.period.get.isTaxAvoidance.isDefined must be (true)
      newProp.get.period.get.taxAvoidanceScheme.isDefined must be (true)
      newProp.get.period.get.taxAvoidancePromoterReference.isDefined must be (true)
      newProp.get.period.get.liabilityPeriods.head.startDate must be (updatedValue.startDate)
      newProp.get.period.get.liabilityPeriods.head.endDate must be (updatedValue.endDate)
    }

    "Saving existing property details DatesLiable updates an existing list with no Periods. Change value so clear future values" in {

      val propertyDetails3Empty = propertyDetails3.copy(period = None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsDatesLiable(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (false)
    }

    "Saving existing property details DatesLiable updates an existing list. Dont change value" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val liabilityPeriod = propertyDetails3.period.flatMap(_.liabilityPeriods.headOption)
      val updatedValue = PropertyDetailsDatesLiable(
        liabilityPeriod.map(_.startDate).getOrElse(new LocalDate("1970-01-01")),
        liabilityPeriod.map(_.endDate).getOrElse(new LocalDate("1970-01-01"))
      )
      val result = TestPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.period.isDefined must be (true)
      newProp.get.calculated.isDefined must be(true)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod.isDefined must be (true)
      newProp.get.period.get.isInRelief.isDefined must be (true)
      newProp.get.period.get.isTaxAvoidance.isDefined must be (true)
      newProp.get.period.get.taxAvoidanceScheme.isDefined must be (true)
      newProp.get.period.get.taxAvoidancePromoterReference.isDefined must be (true)
      newProp.get.period.get.liabilityPeriods.head.startDate must be (updatedValue.startDate)
      newProp.get.period.get.liabilityPeriods.head.endDate must be (updatedValue.endDate)
    }
  }

  "Add DatesLiable" must {

    "Add new DatesLiable to an empty list" in {
      val propertyDetails3Empty = propertyDetails3.copy(period = Some(PropertyDetailsPeriod()))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod = PropertyDetailsDatesLiable(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (1)
      newProp.get.period.get.reliefPeriods.size must be (0)
    }

    "Add new DatesLiable to an existing list" in {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod = PropertyDetailsDatesLiable(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size + 1)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)
    }

    "Add new DatesLiable to an existing list with the same date as an existing one" in {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod = propertyDetails3.period.get.liabilityPeriods.headOption.get
      val updatedPeriod = PropertyDetailsDatesLiable(
        oldPeriod.startDate, oldPeriod.endDate.plusYears(1)
      )
      val result = TestPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)

      val readUpdatedPeriod = newProp.get.period.get.liabilityPeriods.find(p => p.startDate == updatedPeriod.startDate)
      readUpdatedPeriod.isDefined must be (true)
      readUpdatedPeriod.get.endDate must be (oldPeriod.endDate.plusYears(1))
    }
  }


  "Add DatesInRelief" must {
    "Add new DatesInRelief to an empty list" in {
      val propertyDetails3Empty = propertyDetails3.copy(period = Some(PropertyDetailsPeriod()))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod = PropertyDetailsDatesInRelief(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (0)
      newProp.get.period.get.reliefPeriods.size must be (1)
    }

    "Add new DatesInRelief to an existing list" in {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod = PropertyDetailsDatesInRelief(
        new LocalDate("2999-02-03"),new LocalDate("2999-03-04")
      )
      val result = TestPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size  + 1)
    }

    "Add new DatesInRelief to an existing list with the same date as an existing one" in {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod = propertyDetails3.period.get.reliefPeriods.headOption.get
      val updatedPeriod = PropertyDetailsDatesInRelief(
        oldPeriod.startDate, oldPeriod.endDate.plusYears(1)
      )
      val result = TestPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)

      val readUpdatedPeriod = newProp.get.period.get.reliefPeriods.find(p => p.startDate == updatedPeriod.startDate)
      readUpdatedPeriod.isDefined must be (true)
      readUpdatedPeriod.get.endDate must be (oldPeriod.endDate.plusYears(1))
    }
  }

  "Delete property details Period" must {

    "Delete a Period from an existing list" in {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(Matchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod = propertyDetails3.period.get.reliefPeriods.headOption.get
      val result = TestPropertyDetailsService.deleteDraftPeriod(accountRef, propertyDetails3.id, oldPeriod.startDate)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)
      newProp.get.period.get.isFullPeriod.isDefined must be (true)
      newProp.get.period.get.isInRelief.isDefined must be (true)
      newProp.get.period.get.isTaxAvoidance.isDefined must be (true)
      newProp.get.period.get.taxAvoidanceScheme.isDefined must be (true)
      newProp.get.period.get.taxAvoidancePromoterReference.isDefined must be (true)
      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size  - 1)

      val readUpdatedPeriod = newProp.get.period.get.reliefPeriods.find(p => p.startDate ==  oldPeriod.startDate)
      readUpdatedPeriod.isDefined must be (false)
    }
  }
}
