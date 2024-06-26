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

package services

import builders.PropertyDetailsBuilder
import connectors.EtmpReturnsConnector
import models._
import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import repository.{PropertyDetailsCached, PropertyDetailsMongoRepository}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsPeriodsServiceSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockPropertyDetailsCache: PropertyDetailsMongoRepository = mock[PropertyDetailsMongoRepository]
  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {
    class TestPropertyDetailsService extends PropertyDetailsPeriodService {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val propertyDetailsCache: PropertyDetailsMongoRepository = mockPropertyDetailsCache
      override val etmpConnector: EtmpReturnsConnector = mockEtmpConnector
      override val authConnector: AuthConnector = mockAuthConnector
    }

    val testPropertyDetailsService = new TestPropertyDetailsService()
  }

  val accountRef = "ATED-123123"

  override def beforeEach(): Unit = {
    reset(mockPropertyDetailsCache)
    reset(mockAuthConnector)
    reset(mockEtmpConnector)
  }

  val jsonEtmpResponse: String =
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

  implicit val hc: HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("something"))
  val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("2", Some("something else"))
  val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999.99)))

  "Save property details Full Tax Period" must {

    "Saving existing property details full tax period value when we don't have it in an existing list" in new Setup {
      val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
      val testPropertyDetailsPeriod: IsFullTaxPeriod = IsFullTaxPeriod(updatedpropertyDetails4.period.flatMap(_.isFullPeriod).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        updatedpropertyDetails4.id,
        testPropertyDetailsPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details full tax period updates an existing list. Change value so clear future values and add the new period" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
      val isFullPeriod: IsFullTaxPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod.map( x => !x)).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id, isFullPeriod)

      val newProp: Option[PropertyDetails] = await(result)
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

    "Saving existing property details full tax period updates an existing list. Change value so clear future values no period to add" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val isFullPeriod: IsFullTaxPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod.map( x => !x)).getOrElse(false), None)

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id, isFullPeriod)

      val newProp: Option[PropertyDetails] = await(result)
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

    "Saving existing property details full tax period updates an existing list. Dont change value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val testPropertyDetailsDatesLiable: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1))
      val isFullPeriod: IsFullTaxPeriod = IsFullTaxPeriod(propertyDetails3.period.flatMap(_.isFullPeriod).getOrElse(false), Some(testPropertyDetailsDatesLiable))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftFullTaxPeriod(accountRef,
        propertyDetails3.id,
        isFullPeriod
      )

      val newProp: Option[PropertyDetails] = await(result)
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

    "Saving existing property details in relief value when we don't have it in an existing list" in new Setup {
      val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val inRelief: PropertyDetailsInRelief = PropertyDetailsInRelief(updatedpropertyDetails4.period.flatMap(_.isInRelief))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftInRelief(accountRef,
        updatedpropertyDetails4.id, inRelief)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details in relief updates an existing list. Dont clear future values" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val inRelief: PropertyDetailsInRelief = PropertyDetailsInRelief(propertyDetails3.period.flatMap(_.isInRelief.map( x => !x)))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftInRelief(accountRef,
        propertyDetails3.id, inRelief)

      val newProp: Option[PropertyDetails] = await(result)
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

    "Saving existing property details DatesLiable value when we don't have it in an existing list" in new Setup {
      val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val liabilityPeriod: Option[LineItem] = propertyDetails3.period.flatMap(_.liabilityPeriods.headOption)
      val updatedValue: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        liabilityPeriod.map(_.startDate).getOrElse(LocalDate.of(1970, 1, 1)),
        liabilityPeriod.map(_.endDate).getOrElse(LocalDate.of(1970, 1, 1))
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        updatedpropertyDetails4.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details DatesLiable updates an existing list. Change value so clear future values" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
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

    "Saving existing property details DatesLiable updates an existing list with no Periods. Change value so clear future values" in new Setup {

      val propertyDetails3Empty: PropertyDetails = propertyDetails3.copy(period = None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be (true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (false)
    }

    "Saving existing property details DatesLiable updates an existing list. Dont change value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val liabilityPeriod: Option[LineItem] = propertyDetails3.period.flatMap(_.liabilityPeriods.headOption)
      val updatedValue: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        liabilityPeriod.map(_.startDate).getOrElse(LocalDate.of(1970, 1, 1)),
        liabilityPeriod.map(_.endDate).getOrElse(LocalDate.of(1970, 1, 1))
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
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

    "Add new DatesLiable to an empty list" in new Setup {
      val propertyDetails3Empty: PropertyDetails = propertyDetails3.copy(period = Some(PropertyDetailsPeriod()))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (1)
      newProp.get.period.get.reliefPeriods.size must be (0)
    }

    "Add new DatesLiable to an existing list" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size + 1)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)
    }

    "Add new DatesLiable to an existing list with the same date as an existing one" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod: LineItem = propertyDetails3.period.get.liabilityPeriods.headOption.get
      val updatedPeriod: PropertyDetailsDatesLiable = PropertyDetailsDatesLiable(
        oldPeriod.startDate, oldPeriod.endDate.plusYears(1)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesLiable(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)

      val readUpdatedPeriod: Option[LineItem] = newProp.get.period.get.liabilityPeriods.find( p => p.startDate == updatedPeriod.startDate)
      readUpdatedPeriod.isDefined must be (true)
      readUpdatedPeriod.get.endDate must be (oldPeriod.endDate.plusYears(1))
    }
  }


  "Add DatesInRelief" must {
    "Add new DatesInRelief to an empty list" in new Setup {
      val propertyDetails3Empty: PropertyDetails = propertyDetails3.copy(period = Some(PropertyDetailsPeriod()))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3Empty)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod: PropertyDetailsDatesInRelief = PropertyDetailsDatesInRelief(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (0)
      newProp.get.period.get.reliefPeriods.size must be (1)
    }

    "Add new DatesInRelief to an existing list" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedPeriod: PropertyDetailsDatesInRelief = PropertyDetailsDatesInRelief(
        LocalDate.of(2999, 2, 3),LocalDate.of(2999, 3, 4)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size  + 1)
    }

    "Add new DatesInRelief to an existing list with the same date as an existing one" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod: LineItem = propertyDetails3.period.get.reliefPeriods.headOption.get
      val updatedPeriod: PropertyDetailsDatesInRelief = PropertyDetailsDatesInRelief(
        oldPeriod.startDate, oldPeriod.endDate.plusYears(1)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.addDraftDatesInRelief(accountRef,
        propertyDetails3.id, updatedPeriod)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(true)
      newProp.get.calculated.isDefined must be(false)
      newProp.get.period.isDefined must be (true)

      newProp.get.period.get.liabilityPeriods.size must be (propertyDetails3.period.get.liabilityPeriods.size)
      newProp.get.period.get.reliefPeriods.size must be (propertyDetails3.period.get.reliefPeriods.size)

      val readUpdatedPeriod: Option[LineItem] = newProp.get.period.get.reliefPeriods.find( p => p.startDate == updatedPeriod.startDate)
      readUpdatedPeriod.isDefined must be (true)
      readUpdatedPeriod.get.endDate must be (oldPeriod.endDate.plusYears(1))
    }
  }

  "Delete property details Period" must {

    "Delete a Period from an existing list" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
       when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val oldPeriod: LineItem = propertyDetails3.period.get.reliefPeriods.headOption.get
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.deleteDraftPeriod(accountRef, propertyDetails3.id, oldPeriod.startDate)

      val newProp: Option[PropertyDetails] = await(result)
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

      val readUpdatedPeriod: Option[LineItem] = newProp.get.period.get.reliefPeriods.find( p => p.startDate ==  oldPeriod.startDate)
      readUpdatedPeriod.isDefined must be (false)
    }
  }
}
