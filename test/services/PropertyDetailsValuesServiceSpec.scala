/*
 * Copyright 2022 HM Revenue & Customs
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
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Helpers._
import repository.{PropertyDetailsCached, PropertyDetailsMongoRepository}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.DatabaseUpdate

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsValuesServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockPropertyDetailsCache = mock[PropertyDetailsMongoRepository]
  val mockDatabaseUpdate = mock[DatabaseUpdate[Cache]]
  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector = mock[AuthConnector]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {

    class TestPropertyDetailsService extends PropertyDetailsValuesService {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val propertyDetailsCache = mockPropertyDetailsCache
      override val etmpConnector = mockEtmpConnector
      override val authConnector = mockAuthConnector
    }

    val testPropertyDetailsService = new TestPropertyDetailsService()
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

  implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
  lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("something"))
  lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("2", Some("something else"))
  lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getFullPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999.99)))

  "Cache Draft HasValue Change" must {

    "Saving existing property details hasValueChanged value when we don't have it in an existing list" in new Setup {
      lazy val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftHasValueChanged(accountRef,
        updatedpropertyDetails4.id,
        true)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details hasValueChanged updates an existing list. Dont Change value so keep old values" in new Setup {

      val oldPropertyDetails3 = propertyDetails3.copy(value = Some(PropertyDetailsValue(hasValueChanged = Some(true), revaluedValue = Some(BigDecimal(1111.11)))))
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, oldPropertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftHasValueChanged(accountRef,
        propertyDetails3.id, true)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value.get.hasValueChanged must be(Some(true))
      newProp.get.value.get.revaluedValue must be(Some(BigDecimal(1111.11)))

    }

    "Saving existing property details hasValueChanged updates an existing list. Change value so clear future values" in new Setup {

      val oldPropertyDetails3 = propertyDetails3.copy(value = Some(PropertyDetailsValue(hasValueChanged = Some(true), revaluedValue = Some(BigDecimal(1111.11)))))
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, oldPropertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftHasValueChanged(accountRef,
        propertyDetails3.id, false)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value.get.hasValueChanged must be(Some(false))
      newProp.get.value.get.revaluedValue must be(Some(1111.11))

    }

  }

  "Save property details Acquisition" must {

    "Saving existing property details acquisiton value when we don't have it in an existing list" in new Setup {
      lazy val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(accountRef,
        updatedpropertyDetails4.id,
        true)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }


    "Saving existing property details anAcquistion updates an existing list. Dont change value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(accountRef,
        propertyDetails3.id, true)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value.get.anAcquisition must be(Some(true))
      newProp.get.value.get.revaluedValue must be(Some(BigDecimal(1111.11)))
    }

    "Saving existing property details anAcquistion updates an existing list. Change value clears the revalued values" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsAcquisition(accountRef,
        propertyDetails3.id, false)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value.get.anAcquisition must be(Some(false))
      newProp.get.value.get.revaluedValue.isDefined must be(false)
    }
  }

  "Save property details Revalued" must {
    "Saving existing property details Revalued doesnt update the list if no values have changed" in new Setup {

      val updateValue = new PropertyDetailsRevalued(isPropertyRevalued = propertyDetails3.value.get.isPropertyRevalued,
        revaluedValue = propertyDetails3.value.get.revaluedValue,
        revaluedDate = propertyDetails3.value.get.revaluedDate)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsRevalued(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(true)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.isNewBuild.isDefined must be(true)
      newValue.newBuildValue.isDefined must be(true)
      newValue.newBuildDate.isDefined must be(true)
      newValue.notNewBuildValue.isDefined must be(true)
      newValue.notNewBuildDate.isDefined must be(true)
    }
    "Saving existing property details revalued updates an existing list" in new Setup {

      val updateValue = new PropertyDetailsRevalued(isPropertyRevalued = Some(true),
        revaluedValue = Some(BigDecimal(222.22)),
        revaluedDate = Some(new LocalDate("1970-01-01")))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsRevalued(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.value.isDefined must be(true)
      newProp.get.value.get.anAcquisition must be(Some(true))
      newProp.get.value.get.isPropertyRevalued must be(Some(true))
      newProp.get.value.get.revaluedValue must be(Some(BigDecimal(222.22)))
      newProp.get.value.get.revaluedDate must be(Some(new LocalDate("1970-01-01")))
      newProp.get.calculated.isDefined must be(false)
    }

  }

  "Save property details OwnedBefore" must {

    "Saving existing property details OwnedBefore only updates the owned before value if that is all that has changed" in new Setup {

      val updateValue = new PropertyDetailsOwnedBefore(Some(true), Some(BigDecimal(333.22)))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear must be(Some(true))
      newValue.ownedBeforePolicyYearValue must be(Some(BigDecimal(333.22)))

      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isNewBuild.isDefined must be(true)
      newValue.newBuildValue.isDefined must be(true)
      newValue.newBuildDate.isDefined must be(true)
      newValue.notNewBuildValue.isDefined must be(true)
      newValue.notNewBuildDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

    }
    "Saving existing property details creates the value object if we dont have one" in new Setup {

      val updateValue = new PropertyDetailsOwnedBefore(Some(false), Some(BigDecimal(333.22)))

      val propertyDetails3NoValue = propertyDetails3.copy(value = None)
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3NoValue)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsOwnedBefore(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.isOwnedBeforePolicyYear must be(Some(false))
      newValue.ownedBeforePolicyYearValue must be(Some(BigDecimal(333.22)))
    }

  }

  "Cache property details IsNewBuild" must {
    "Should not update IsNewBuild value if it has not been changed" in new Setup {
      val updateValue = new PropertyDetailsIsNewBuild(isNewBuild = propertyDetails3.value.get.isNewBuild)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsIsNewBuild(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.value.isDefined must be(true)

      val newValue = newProp.get.value.get
      newValue.isNewBuild.isDefined must be(true)
      newValue.isNewBuild must be(updateValue.isNewBuild)
    }

  }

  "Cache property details new build dates" must {
    "not update the held value if it has not been changed" in new Setup {
      val updateValue = new PropertyDetailsNewBuildDates(
        newBuildOccupyDate = propertyDetails3.value.get.newBuildDate,
        newBuildRegisterDate = propertyDetails3.value.get.localAuthRegDate
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.value.isDefined must be(true)

      val newValue = newProp.get.value.get
      newValue.newBuildDate must be(updateValue.newBuildOccupyDate)
      newValue.localAuthRegDate must be(updateValue.newBuildRegisterDate)
    }

    "update the value in the cache if it has been changed" in new Setup {
      val updateValue = new PropertyDetailsNewBuildDates()

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))

      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsNewBuildDates(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.newBuildDate must be(updateValue.newBuildOccupyDate)
      newValue.localAuthRegDate must be(updateValue.newBuildRegisterDate)

    }

  }

  "Cache property details NewBuildValue" must {
    "not update the list of values if non of the values have changed" in new Setup {
      val updateValue = new PropertyDetailsNewBuildValue(
        newBuildValue = propertyDetails3.value.get.newBuildValue
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(true)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.newBuildValue must be(updateValue.newBuildValue)
    }

    "update the list of values if the value of the new build has been changed" in new Setup {
      val updateValue = new PropertyDetailsNewBuildValue(
        newBuildValue = Some(12321.12)
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsNewBuildValue(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.newBuildValue must be(updateValue.newBuildValue)
    }

  }

  "Cache property details value on acquisition" must {
    "not update the cache when the value on acquisition has not changed" in new Setup {
      val updateValue = new PropertyDetailsValueOnAcquisition(
        acquiredValue = propertyDetails3.value.get.notNewBuildValue
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(true)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.notNewBuildValue must be(updateValue.acquiredValue)
    }
    "update the value of the cache when the value on acquistion has changed" in new Setup {

      val updateValue = new PropertyDetailsValueOnAcquisition(
        acquiredValue = Some(120000.000)
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsValueAcquired(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.notNewBuildValue must be(updateValue.acquiredValue)
    }
  }

  "Cache property details date of acquisition" must {
    "not update the cache when the value of acquisition has not changed" in new Setup {
      val updateValue = PropertyDetailsDateOfAcquisition(
        propertyDetails3.value.get.notNewBuildDate
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(true)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.notNewBuildDate must be(updateValue.acquiredDate)

    }

    "update the value of the cache when the date of acquistion has changed" in new Setup {
      val updateValue = PropertyDetailsDateOfAcquisition(Some(new LocalDate("2012-01-01")))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsDatesAcquired(
        accountRef,
        propertyDetails3.id,
        updateValue
      )

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.notNewBuildDate must be(updateValue.acquiredDate)
    }
  }

  "Save property details ProfessionallyValued" must {
    "Saving existing property details ProfessionallyValued doesnt update the list if no values have changed" in new Setup {

      val updateValue = new PropertyDetailsProfessionallyValued(isValuedByAgent = propertyDetails3.value.get.isValuedByAgent)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(true)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.isValuedByAgent.isDefined must be(true)

      newValue.isNewBuild.isDefined must be(true)
      newValue.newBuildValue.isDefined must be(true)
      newValue.newBuildDate.isDefined must be(true)
      newValue.notNewBuildValue.isDefined must be(true)
      newValue.notNewBuildDate.isDefined must be(true)
    }
    "Saving existing property details ProfessionallyValued updates an existing list" in new Setup {


      val updateValue = new PropertyDetailsProfessionallyValued(Some(false))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsProfessionallyValued(accountRef,
        propertyDetails3.id,
        updateValue)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)

      newProp.get.value.isDefined must be(true)
      val newValue = newProp.get.value.get
      newValue.anAcquisition must be(Some(true))
      newValue.isOwnedBeforePolicyYear.isDefined must be(true)
      newValue.ownedBeforePolicyYearValue.isDefined must be(true)
      newValue.isNewBuild.isDefined must be(true)
      newValue.newBuildValue.isDefined must be(true)
      newValue.newBuildDate.isDefined must be(true)
      newValue.notNewBuildValue.isDefined must be(true)
      newValue.notNewBuildDate.isDefined must be(true)

      newValue.isPropertyRevalued.isDefined must be(true)
      newValue.revaluedValue.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)
      newValue.revaluedDate.isDefined must be(true)

      newValue.isValuedByAgent must be(Some(false))

    }

  }

}
