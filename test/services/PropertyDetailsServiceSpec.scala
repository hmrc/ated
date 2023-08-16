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


import builders.{AuthFunctionalityHelper, ChangeLiabilityReturnBuilder, PropertyDetailsBuilder}
import connectors.{EmailConnector, EmailSent, EtmpReturnsConnector}
import models._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import repository._
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, InternalServerException, SessionId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PropertyDetailsServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {

  val mockPropertyDetailsCache = mock[PropertyDetailsMongoRepository]
  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector = mock[EmailConnector]
  val mockAuditConnector = mock[AuditConnector]
  implicit val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  val mockAudit = mock[Audit]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {
    class TestPropertyDetailsService extends PropertyDetailsService {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val propertyDetailsCache = mockPropertyDetailsCache
      override val etmpConnector = mockEtmpConnector
      override val authConnector = mockAuthConnector
      override val subscriptionDataService = mockSubscriptionDataService
      override val emailConnector = mockEmailConnector
      override val audit = mockAudit
      val auditConnector = mockAuditConnector
    }
    val testPropertyDetailsService = new TestPropertyDetailsService()
  }

  val accountRef = "ATED-123123"

  override def beforeEach(): Unit = {
    reset(mockPropertyDetailsCache)
    reset(mockAuthConnector)
    reset(mockEtmpConnector)
    reset(mockSubscriptionDataService)
    reset(mockEmailConnector)
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
      |      "propertyKey": "2",
      |      "liabilityAmount": "1234.12",
      |      "paymentReference": "aaaaaaaaaaaaaa",
      |      "formBundleNumber": "012345678912"
      |    },
      |    {
      |      "mode": "Pre-Calculation",
      |      "propertyKey": "1",
      |      "liabilityAmount": "999.99"
      |    }
      |  ]
      |}
    """.stripMargin

  val successResponseJson = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")

  implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

  "fetch Property Details" must {

    "fetch the cached Property Details when we have some" in new Setup {
      lazy val testPropertyDetails = List(PropertyDetailsBuilder.getPropertyDetails("1"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetails))
      val result = testPropertyDetailsService.retrieveDraftPropertyDetails(accountRef)

      await(result) must be(testPropertyDetails)
    }

    "fetch an empty list when we have no cached Property Details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(Nil))
      val result = testPropertyDetailsService.retrieveDraftPropertyDetails(accountRef)

      await(result).isEmpty must be(true)
    }
    "fetch the cached Property Details when we have an ID and no matching data" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(Nil))
      val result = testPropertyDetailsService.retrieveDraftPropertyDetail(accountRef, "3")

      await(result).isDefined must be(false)
    }


    "fetch the cached Property Details when we have an ID and matching data" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1")
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2")
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3")
      lazy val testPropertyDetailsList = Seq(propertyDetails1, propertyDetails2, propertyDetails3)
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetailsList))
      val result = testPropertyDetailsService.retrieveDraftPropertyDetail(accountRef, "1")

      await(result) must be(Some(propertyDetails1))
    }


    "fetch the cached Property Details when we have an a Period Key" in new Setup {
      lazy val tooEarly = PropertyDetailsBuilder.getPropertyDetails("1").copy(periodKey = 2013)
      lazy val tooLate = PropertyDetailsBuilder.getPropertyDetails("2").copy(periodKey = 2015)
      lazy val entirePeriod = PropertyDetailsBuilder.getPropertyDetails("3").copy(periodKey = 2014)
      lazy val startOfPeriod = PropertyDetailsBuilder.getPropertyDetails("4").copy(periodKey = 2014)

      val testPropertyDetailsList = Seq(tooEarly, tooLate, entirePeriod, startOfPeriod)
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetailsList))
      val result = testPropertyDetailsService.retrievePeriodDraftPropertyDetails(accountRef, 2014)

      val resultList = await(result)
      resultList.head.id must be("3")
      resultList(1).id must be("4")
      resultList.size must be(2)
    }
  }


  "Delete property details" must {
    "remove the delete success details if we find one" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PropertyDetailsDeleted))

      val result = testPropertyDetailsService.deleteDraftPropertyDetail(accountRef, "2")

      val update = await(result)
      update must be (PropertyDetailsDeleted)
    }

    "return delete error if we don't find one to delete" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PropertyDetailsDeleteError))

      val result = testPropertyDetailsService.deleteDraftPropertyDetail(accountRef, "4")

      val update = await(result)
      update must be (PropertyDetailsDeleteError)
    }
  }

  "Delete property details by property id" must {
    "remove the selected property details if we find one" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.eq(accountRef), ArgumentMatchers.eq("1")))
        .thenReturn(Future.successful(PropertyDetailsDeleted))

      when(mockPropertyDetailsCache.fetchPropertyDetailsById(ArgumentMatchers.eq(accountRef), ArgumentMatchers.eq("1")))
        .thenReturn(Future.successful(Seq()))
      val result = testPropertyDetailsService.deleteChargeableDraft(accountRef, "1")
      val updateList = await(result)
      updateList.size must be(0)
    }

  }


  "Create property details" must {

    "Create property details and return a new Id" in new Setup {
      lazy val addressRef = PropertyDetailsBuilder.getPropertyDetailsAddress(None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef)).thenReturn(Future.successful(Nil))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.createDraftPropertyDetails(accountRef, 2014, addressRef)

      val updateDetails = await(result)
      updateDetails.isDefined must be(true)
    }

    "Create new property details, updates an existing list" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      lazy val updatedPropertyDetails4 = PropertyDetailsBuilder.getPropertyDetailsAddress(Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.createDraftPropertyDetails(accountRef,
        propertyDetails3.periodKey,
        updatedPropertyDetails4)

      val updateDetails = await(result)
      updateDetails.isDefined must be(true)
      updateDetails.get.addressProperty.postcode must be(Some("something better"))
    }
  }

  "Save property details Address" must {

    "Saving existing property details when we have some, updates an existing list" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999)))

      lazy val updatedpropertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsAddress(accountRef,
        updatedpropertyDetails3.id,
        updatedpropertyDetails3.addressProperty)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(updatedpropertyDetails3.periodKey)
      newProp.get.addressProperty must be(updatedpropertyDetails3.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value must be(propertyDetails3.value)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)
    }
  }

  "Calculates property details" must {
    lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
    lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
    lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

    "Fail to Calculate when we don't have it in an existing list" in new Setup {

      lazy val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef,
        updatedpropertyDetails4.id)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Return the existing Liability Amount if we have it already calculated" in new Setup {
      lazy val calcPropertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"), liabilityAmount = Some(BigDecimal(123.22)))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(calcPropertyDetails1)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef, calcPropertyDetails1.id)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails1.periodKey)
      newProp.get.addressProperty must be(propertyDetails1.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value must be(propertyDetails1.value)
      newProp.get.period.isDefined must be(true)
      newProp.get.calculated.get.liabilityAmount.isDefined must be(true)
      newProp.get.calculated.get.liabilityAmount.get must be(BigDecimal(123.22))
    }

    "Calculate the liability Amount if don't already have calculated" in new Setup {
      lazy val calcPropertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"), liabilityAmount = Some(BigDecimal(123.22))).copy(calculated = None)

      val successResponse = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(calcPropertyDetails1)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef,
        calcPropertyDetails1.id)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails1.periodKey)
      newProp.get.addressProperty must be(propertyDetails1.addressProperty)
      newProp.get.value.isDefined must be(true)
      newProp.get.value must be(propertyDetails1.value)
      newProp.get.period.isDefined must be(true)
      newProp.get.calculated.get.liabilityAmount.isDefined must be(true)
      newProp.get.calculated.get.liabilityAmount.get must be(BigDecimal(999.99))
    }
  }

  "Save property details Title" must {
    lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
    lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
    lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))
    "Saving existing property details title when we don't have it in an existing list" in new Setup {

      lazy val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsTitle(accountRef,
        updatedpropertyDetails4.id,
        updatedpropertyDetails4.title.get)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details title updates an existing list" in new Setup {
      lazy val updatedpropertyDetails3 = new PropertyDetailsTitle("updateTitle here")

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result = testPropertyDetailsService.cacheDraftPropertyDetailsTitle(accountRef,
        propertyDetails3.id,
        updatedpropertyDetails3)

      val newProp = await(result)
      newProp.isDefined must be(true)
      newProp.get.periodKey must be(propertyDetails3.periodKey)
      newProp.get.addressProperty must be(propertyDetails3.addressProperty)
      newProp.get.title.isDefined must be(true)
      newProp.get.title.get must be(updatedpropertyDetails3)
      newProp.get.period.isDefined must be(true)
      newProp.get.period must be(propertyDetails3.period)
      newProp.get.calculated.isDefined must be(false)
    }

  }

  "Save property details Tax Avoidance" must {
    lazy val propertyDetails1 = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("something"))
    lazy val propertyDetails2 = PropertyDetailsBuilder.getFullPropertyDetails("2", Some("something else"))
    lazy val propertyDetails3 = PropertyDetailsBuilder.getFullPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999.99)))
    "Saving existing property details Tax Avoidance value when we don't have it in an existing list" in new Setup {
      val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsTaxAvoidance(propertyDetails3.period.flatMap(_.isTaxAvoidance.map(x => !x)))
      val result = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        updatedpropertyDetails4.id, updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details Tax Avoidance updates an existing list. Change value so clear future values" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsTaxAvoidance(
        propertyDetails3.period.flatMap(_.isTaxAvoidance.map(x => !x)),
        propertyDetails3.period.flatMap(_.taxAvoidanceScheme),
        propertyDetails3.period.flatMap(_.taxAvoidancePromoterReference)
      )
      val result = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
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
      newProp.get.period.get.isTaxAvoidance must be (updatedValue.isTaxAvoidance)
      newProp.get.period.get.taxAvoidanceScheme must be (updatedValue.taxAvoidanceScheme)
      newProp.get.period.get.taxAvoidancePromoterReference must be (updatedValue.taxAvoidancePromoterReference)
    }

    "Saving existing property details Tax Avoidance updates an existing list. Dont change value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsTaxAvoidance(
        propertyDetails3.period.flatMap(_.isTaxAvoidance),
        propertyDetails3.period.flatMap(_.taxAvoidanceScheme),
        propertyDetails3.period.flatMap(_.taxAvoidancePromoterReference)
      )
      val result = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
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
      newProp.get.period.get.isTaxAvoidance must be (updatedValue.isTaxAvoidance)
      newProp.get.period.get.taxAvoidanceScheme must be (updatedValue.taxAvoidanceScheme)
      newProp.get.period.get.taxAvoidancePromoterReference must be (updatedValue.taxAvoidancePromoterReference)
    }
  }

  "Save property details Supporting Info" must {
    lazy val propertyDetails1 = PropertyDetailsBuilder.getFullPropertyDetails("1", Some("something"))
    lazy val propertyDetails2 = PropertyDetailsBuilder.getFullPropertyDetails("2", Some("something else"))
    lazy val propertyDetails3 = PropertyDetailsBuilder.getFullPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(999.99)))
    "Saving existing property details in relief value when we don't have it in an existing list" in new Setup {
      val updatedpropertyDetails4 = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsSupportingInfo("Updated " + propertyDetails3.period.flatMap(_.supportingInfo).getOrElse(""))
      val result = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
        updatedpropertyDetails4.id,
        updatedValue)

      val newProp = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details  in relief updates an existing list. Change value so clear future values" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsSupportingInfo("Updated " + propertyDetails3.period.flatMap(_.supportingInfo).getOrElse(""))
      val result = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
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
      newProp.get.period.get.supportingInfo must be (Some(updatedValue.supportingInfo))
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)
    }

    "Saving existing property details anAcquistion updates an existing list. Dont change value" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue = PropertyDetailsSupportingInfo(propertyDetails3.period.flatMap(_.supportingInfo).getOrElse(""))
      val result = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
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
      newProp.get.period.get.supportingInfo must be (Some(updatedValue.supportingInfo))
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)
    }
  }

  "cacheDraftHasBankDetails" must {
    lazy val protectedBankDetails = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
    lazy val propertyDetailsWithBankDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(periodKey = 2016, formBundle = "1", bankDetails = Some(protectedBankDetails))
    lazy val propertyDetailsNoBankDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(periodKey = 2016, formBundle = "1", bankDetails = None)
    "update the has bank details when we have bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, true))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails.isDefined must be(true)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "clear the bank details if we have some and the hasBankDetails is false" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, false))

      result.get.bankDetails.get.hasBankDetails must be(false)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "create the bank details model if we don't already have one" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsNoBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, true))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "clear the bank details if we have some and the flag is false" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, false))

      result.get.bankDetails.get.hasBankDetails must be(false)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return None if form-bundle not-found in cache" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Nil))
      val result = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, "", true))
      result must be(None)
    }
  }

  "cacheDraftBankDetails" must {
    lazy val protectedBankDetails = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
    lazy val propertyDetailsWithBankDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(periodKey = 2016, formBundle = "1", bankDetails = Some(protectedBankDetails))
    lazy val propertyDetailsNoBankDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(periodKey = 2016, formBundle = "1", bankDetails = None)
    "return Some(PropertyDetails) if form-bundle found in cache with bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val bankDetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
      //val expected = updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle1, protectedBankDetails)
      val result = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, propertyDetailsWithBankDetails.id, bankDetails))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails must be(Some(bankDetails))
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return Some(PropertyDetails) if form-bundle found in cache with no bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsNoBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val bankDetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
      //val expected = updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle1, protectedBankDetails)
      val result = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, propertyDetailsNoBankDetails.id, bankDetails))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails must be(Some(bankDetails))
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return None if form-bundle not-found in cache" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Nil))
      val bankDetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails
      val result = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, "", bankDetails.bankDetails.get))
      result must be(None)
    }
  }


  "Retrieve the Liability Amount for the PropertyDetails" must {
    "Get the Liability Amount " in new Setup {
      lazy val propertyDetailsExample = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val successResponse = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
      val result = testPropertyDetailsService.getLiabilityAmount(accountRef, "1", propertyDetailsExample)

      val liabilityAmount = await(result)
      liabilityAmount must be(Some(999.99))
    }

    "Return None if we have no Liability Amount " in new Setup {
      lazy val propertyDetailsExample = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val successResponse = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
      val result = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val liabilityAmount = await(result)
      liabilityAmount.isDefined must be(false)
    }

    "Fail if we have BAD_REQUEST" in new Setup {
      lazy val propertyDetailsExample = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val failureResponse = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, failureResponse, Map.empty[String, Seq[String]])))
      val result = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val thrown = the[BadRequestException] thrownBy await(result)
      thrown.getMessage must include("Error")
    }

    "Fail if we have dont find Liability Amount" in new Setup {
      lazy val propertyDetailsExample = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val failureResponse = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, failureResponse, Map.empty[String, Seq[String]])))
      val result = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val thrown = the[InternalServerException] thrownBy await(result)
      thrown.getMessage must include("No Liability Amount Found")
      }

    "Fail if we have dont have valid details " in new Setup {
      lazy val propertyDetailsPopulated = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))
      val propertyDetailsExample = propertyDetailsPopulated.copy(period = None, calculated = None)

      val failureResponse = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, failureResponse, Map.empty[String, Seq[String]])))
      val thrown = the[InternalServerException] thrownBy testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      thrown.getMessage must include("Invalid Data for the request")
    }
  }

  "Submit the Property Details from the Cache" must {
    "Submit the property details and delete the item from the cache if it's a valid id" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"), liabilityAmount = Some(BigDecimal(999.99)))
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      type Retrieval = Option[Name]
      val testEnrolments: Set[Enrolment] = Set(Enrolment("HMRC-ATED-ORG", Seq(EnrolmentIdentifier("AgentRefNumber", "XN1200000100001")), "activated"))
      val name = Name(Some("gary"),Some("bloggs"))
      val enrolmentsWithName: Retrieval = Some(name)

      val successResponse = Json.parse(jsonEtmpResponse)
      when(mockAuthConnector.authorise[Any](any(), any())(any(), any()))
        .thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PropertyDetailsDeleted))
      when(mockEtmpConnector.submitReturns(ArgumentMatchers.eq(accountRef),
        ArgumentMatchers.any[SubmitEtmpReturnsRequest]())(ArgumentMatchers.any(), ArgumentMatchers.any())) thenReturn {
        Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]]))
      }
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      when(mockSubscriptionDataService.retrieveSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
      when(mockEmailConnector.sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())) thenReturn Future.successful(EmailSent)

      val result = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "1")
      await(result).status must be(OK)
      verify(mockEmailConnector, times(1)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }

    "return a NOT_FOUND if the property details doesn't exist for this id" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      mockRetrievingNoAuthRef()
      when(mockSubscriptionDataService.retrieveSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

      val result = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "4")
      await(result).status must be(NOT_FOUND)
      verify(mockEmailConnector, times(0)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }

    "return a NOT_FOUND if the property details are invalid for this id" in new Setup {
      lazy val propertyDetails1 = PropertyDetailsBuilder.getPropertyDetails("1", Some("something")).copy(period = None, calculated = None)
      lazy val propertyDetails2 = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3 = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))
      lazy val propertyDetailsExample = propertyDetails1.copy(period = None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetailsExample, propertyDetails2, propertyDetails3)))
      mockRetrievingNoAuthRef()
      when(mockSubscriptionDataService.retrieveSubscriptionData(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

      val result = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "1")
      await(result).status must be(NOT_FOUND)
      verify(mockEmailConnector, times(0)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }
  }

}
