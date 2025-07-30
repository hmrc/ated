/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
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

  val mockPropertyDetailsCache: PropertyDetailsMongoRepository = mock[PropertyDetailsMongoRepository]
  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService: SubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  implicit val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  val mockAudit: Audit = mock[Audit]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  trait Setup {
    class TestPropertyDetailsService extends PropertyDetailsService {
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      override val propertyDetailsCache: PropertyDetailsMongoRepository = mockPropertyDetailsCache
      override val etmpConnector: EtmpReturnsConnector = mockEtmpConnector
      override val authConnector: AuthConnector = mockAuthConnector
      override val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
      override val emailConnector: EmailConnector = mockEmailConnector
      override val audit: Audit = mockAudit
      val auditConnector: AuditConnector = mockAuditConnector
    }

    val periodKey2013: Int = 2013
    val periodKey2014: Int = 2014
    val periodKey2015: Int = 2015
    val liabilityAmount999: Int = 999
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

  val successResponseJson: JsValue = Json.parse(
    """
  {
    "safeId": "1111111111",
    "organisationName": "Test Org",
    "address": [
      {
        "name1": "name1",
        "name2": "name2",
        "addressDetails": {
          "addressType": "Correspondence",
          "addressLine1": "address line 1",
          "addressLine2": "address line 2",
          "addressLine3": "address line 3",
          "addressLine4": "address line 4",
          "postalCode": "ZZ1 1ZZ",
          "countryCode": "GB"
        },
        "contactDetails": {
          "phoneNumber": "01234567890",
          "mobileNumber": "0712345678",
          "emailAddress": "test@mail.com"
        }
      }
    ]
  }
  """
  )

  implicit val hc:HeaderCarrier = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

  "fetch Property Details" must {

    "fetch the cached Property Details when we have some" in new Setup {
      lazy val testPropertyDetails: Seq[PropertyDetails] = List(PropertyDetailsBuilder.getPropertyDetails("1"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetails))
      val result: Future[Seq[PropertyDetails]] = testPropertyDetailsService.retrieveDraftPropertyDetails(accountRef)

      await(result) must be(testPropertyDetails)
    }

    "fetch an empty list when we have no cached Property Details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(Nil))
      val result: Future[Seq[PropertyDetails]] = testPropertyDetailsService.retrieveDraftPropertyDetails(accountRef)

      await(result).isEmpty must be(true)
    }
    "fetch the cached Property Details when we have an ID and no matching data" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(Nil))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.retrieveDraftPropertyDetail(accountRef, "3")

      await(result).isDefined must be(false)
    }


    "fetch the cached Property Details when we have an ID and matching data" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1")
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2")
      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3")
      lazy val testPropertyDetailsList: Seq[PropertyDetails] = Seq(propertyDetails1, propertyDetails2, propertyDetails3)
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetailsList))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.retrieveDraftPropertyDetail(accountRef, "1")

      await(result) must be(Some(propertyDetails1))
    }


    "fetch the cached Property Details when we have an a Period Key" in new Setup {
      lazy val tooEarly: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1").copy(periodKey = periodKey2013)
      lazy val tooLate: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2").copy(periodKey = periodKey2015)
      lazy val entirePeriod: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3").copy(periodKey = periodKey2014)
      lazy val startOfPeriod: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4").copy(periodKey = periodKey2014)

      val testPropertyDetailsList: Seq[PropertyDetails] = Seq(tooEarly, tooLate, entirePeriod, startOfPeriod)
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.any())).thenReturn(Future.successful(testPropertyDetailsList))
      val result: Future[Seq[PropertyDetails]] = testPropertyDetailsService.retrievePeriodDraftPropertyDetails(accountRef, periodKey2014)

      val resultList: Seq[PropertyDetails] = await(result)
      resultList.head.id must be("3")
      resultList(1).id must be("4")
      resultList.size must be(2)
    }
  }


  "Delete property details" must {
    "remove the delete success details if we find one" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PropertyDetailsDeleted))

      val result: Future[PropertyDetailsDelete] = testPropertyDetailsService.deleteDraftPropertyDetail(accountRef, "2")

      val update: PropertyDetailsDelete = await(result)
      update must be (PropertyDetailsDeleted)
    }

    "return delete error if we don't find one to delete" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(PropertyDetailsDeleteError))

      val result: Future[PropertyDetailsDelete] = testPropertyDetailsService.deleteDraftPropertyDetail(accountRef, "4")

      val update: PropertyDetailsDelete = await(result)
      update must be (PropertyDetailsDeleteError)
    }
  }

  "Delete property details by property id" must {
    "remove the selected property details if we find one" in new Setup {
      when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.eq(accountRef), ArgumentMatchers.eq("1")))
        .thenReturn(Future.successful(PropertyDetailsDeleted))

      when(mockPropertyDetailsCache.fetchPropertyDetailsById(ArgumentMatchers.eq(accountRef), ArgumentMatchers.eq("1")))
        .thenReturn(Future.successful(Seq()))
      val result: Future[Seq[PropertyDetails]] = testPropertyDetailsService.deleteChargeableDraft(accountRef, "1")
      val updateList: Seq[PropertyDetails] = await(result)
      updateList.size must be(0)
    }

  }


  "Create property details" must {

    "Create property details and return a new Id" in new Setup {
      lazy val addressRef: PropertyDetailsAddress = PropertyDetailsBuilder.getPropertyDetailsAddress(None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef)).thenReturn(Future.successful(Nil))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.createDraftPropertyDetails(accountRef, periodKey2014, addressRef)

      val updateDetails: Option[PropertyDetails] = await(result)
      updateDetails.isDefined must be(true)
    }

    "Create new property details, updates an existing list" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      lazy val updatedPropertyDetails4: PropertyDetailsAddress = PropertyDetailsBuilder.getPropertyDetailsAddress(Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.createDraftPropertyDetails(accountRef,
        propertyDetails3.periodKey,
        updatedPropertyDetails4)

      val updateDetails: Option[PropertyDetails] = await(result)
      updateDetails.isDefined must be(true)
      updateDetails.get.addressProperty.postcode must be(Some("something better"))
    }
  }

  "Save property details Address" must {

    "Saving existing property details when we have some, updates an existing list" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))

      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder
        .getPropertyDetails("3", Some("something more"), liabilityAmount = Some(BigDecimal(liabilityAmount999)))

      lazy val updatedpropertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftPropertyDetailsAddress(accountRef,
        updatedpropertyDetails3.id,
        updatedpropertyDetails3.addressProperty)

      val newProp: Option[PropertyDetails] = await(result)
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

      lazy val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef,
        updatedpropertyDetails4.id)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Return the existing Liability Amount if we have it already calculated" in new Setup {
      lazy val calcPropertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails(
        "1",
        Some("something"),
        liabilityAmount = Some(BigDecimal(123.22))
      )

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(calcPropertyDetails1)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef, calcPropertyDetails1.id)

      val newProp: Option[PropertyDetails] = await(result)
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
      lazy val calcPropertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails(
        "1",
        Some("something"),
        liabilityAmount = Some(BigDecimal(123.22))
      ).copy(calculated = None)

      val successResponse: JsValue = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef),
        ArgumentMatchers.any[SubmitEtmpReturnsRequest]
      )(ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(calcPropertyDetails1)))

      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      mockRetrievingNoAuthRef()

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.calculateDraftPropertyDetails(accountRef,
        calcPropertyDetails1.id)

      val newProp: Option[PropertyDetails] = await(result)
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

      lazy val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftPropertyDetailsTitle(accountRef,
        updatedpropertyDetails4.id,
        updatedpropertyDetails4.title.get)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details title updates an existing list" in new Setup {
      lazy val updatedpropertyDetails3 = new PropertyDetailsTitle("updateTitle here")

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftPropertyDetailsTitle(accountRef,
        propertyDetails3.id,
        updatedpropertyDetails3)

      val newProp: Option[PropertyDetails] = await(result)
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
      val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsTaxAvoidance = PropertyDetailsTaxAvoidance(propertyDetails3.period.flatMap(_.isTaxAvoidance.map( x => !x)))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        updatedpropertyDetails4.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Updating tax avoidance from yes to no removes avoidance scheme and avoidance promoter reference" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))

      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsTaxAvoidance = PropertyDetailsTaxAvoidance(Some(false))
   
      testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        propertyDetails3.id, updatedValue)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3.copy(period = propertyDetails3.period.map(_.copy(
          isTaxAvoidance = Some(false)
        ))))))

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.get.period.get.isTaxAvoidance must be (updatedValue.isTaxAvoidance)
      newProp.get.period.get.taxAvoidanceScheme must be (None)
      newProp.get.period.get.taxAvoidancePromoterReference must be (None)
    }

    "Updating tax avoidance scheme does not remove isAvoidance value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))

      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsTaxAvoidance = PropertyDetailsTaxAvoidance(
        taxAvoidanceScheme = Some("New Scheme")
      )

      testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        propertyDetails3.id, updatedValue)


      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
        propertyDetails3.id, updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.get.period.get.isTaxAvoidance must be (Some(true))
      newProp.get.period.get.taxAvoidanceScheme must be (Some("New Scheme"))
      newProp.get.period.get.taxAvoidancePromoterReference must be (Some("taxAvoidancePromoterReference"))
    }

    "Saving existing property details Tax Avoidance updates an existing list. Change value so clear future values" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsTaxAvoidance = PropertyDetailsTaxAvoidance(
        propertyDetails3.period.flatMap(_.isTaxAvoidance.map(x => !x)),
        propertyDetails3.period.flatMap(_.taxAvoidanceScheme),
        propertyDetails3.period.flatMap(_.taxAvoidancePromoterReference)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
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
      newProp.get.period.get.isTaxAvoidance must be (updatedValue.isTaxAvoidance)
      newProp.get.period.get.taxAvoidanceScheme must be (updatedValue.taxAvoidanceScheme)
      newProp.get.period.get.taxAvoidancePromoterReference must be (updatedValue.taxAvoidancePromoterReference)
    }

    "Saving existing property details Tax Avoidance updates an existing list. Dont change value" in new Setup {

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsTaxAvoidance = PropertyDetailsTaxAvoidance(
        propertyDetails3.period.flatMap(_.isTaxAvoidance),
        propertyDetails3.period.flatMap(_.taxAvoidanceScheme),
        propertyDetails3.period.flatMap(_.taxAvoidancePromoterReference)
      )
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftTaxAvoidance(accountRef,
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
      val updatedpropertyDetails4: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("4", Some("something better"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsSupportingInfo = PropertyDetailsSupportingInfo(
        "Updated " + propertyDetails3.period.flatMap(_.supportingInfo).getOrElse("")
      )

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
        updatedpropertyDetails4.id,
        updatedValue)

      val newProp: Option[PropertyDetails] = await(result)
      newProp.isDefined must be(false)
    }

    "Saving existing property details  in relief updates an existing list. Change value so clear future values" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsSupportingInfo = PropertyDetailsSupportingInfo(
        "Updated " + propertyDetails3.period.flatMap(_.supportingInfo).getOrElse("")
      )

      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
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
      newProp.get.period.get.supportingInfo must be (Some(updatedValue.supportingInfo))
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)
    }

    "Saving existing property details anAcquistion updates an existing list. Dont change value" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val updatedValue: PropertyDetailsSupportingInfo = PropertyDetailsSupportingInfo(propertyDetails3.period.flatMap(_.supportingInfo).getOrElse(""))
      val result: Future[Option[PropertyDetails]] = testPropertyDetailsService.cacheDraftSupportingInfo(accountRef,
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
      newProp.get.period.get.supportingInfo must be (Some(updatedValue.supportingInfo))
      newProp.get.period.get.liabilityPeriods.isEmpty must be (false)
    }
  }

  "cacheDraftHasBankDetails" must {
    lazy val protectedBankDetails: BankDetailsModel = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
    val periodKey2016: Int = 2016

    lazy val propertyDetailsWithBankDetails: PropertyDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(
      periodKey = periodKey2016,
      formBundle = "1",
      bankDetails = Some(protectedBankDetails)
    )

    lazy val propertyDetailsNoBankDetails: PropertyDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(
      periodKey = periodKey2016,
      formBundle = "1",
      bankDetails = None
    )

    "update the has bank details when we have bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result: Option[PropertyDetails] = await(
        testPropertyDetailsService.cacheDraftHasBankDetails(accountRef,
          propertyDetailsWithBankDetails.id,
          hasBankDetails = true))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails.isDefined must be(true)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "clear the bank details if we have some and the hasBankDetails is false" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result: Option[PropertyDetails] = await(
        testPropertyDetailsService.cacheDraftHasBankDetails(
        accountRef, propertyDetailsWithBankDetails.id, hasBankDetails = false))

      result.get.bankDetails.get.hasBankDetails must be(false)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "create the bank details model if we don't already have one" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsNoBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result: Option[PropertyDetails] = await(
        testPropertyDetailsService
        .cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, hasBankDetails = true))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "clear the bank details if we have some and the flag is false" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val result: Option[PropertyDetails] = await(
        testPropertyDetailsService
          .cacheDraftHasBankDetails(accountRef, propertyDetailsWithBankDetails.id, hasBankDetails = false))

      result.get.bankDetails.get.hasBankDetails must be(false)
      result.get.bankDetails.get.bankDetails.isDefined must be(false)
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return None if form-bundle not-found in cache" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Nil))
      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftHasBankDetails(accountRef, "", hasBankDetails = true))
      result must be(None)
    }
  }

  "cacheDraftHasUkBankAccount" must {

    val propertyId = "prop-123"
    val atedRefNo = "ATED123"
    val periodKey2017: Int = 2017

    lazy val baseBankDetails: BankDetails = BankDetails(
      hasUKBankAccount = Some(true),
      accountName = Some("Test Name"),
      accountNumber = Some("87654321"),
      sortCode = Some(SortCode("10", "20", "30")),
      bicSwiftCode = Some(BicSwiftCode("12345678901")),
      iban = Some(Iban("GB00IBAN123456789"))
    )

    lazy val bankDetailsModelWithAllFields: BankDetailsModel = BankDetailsModel(
      bankDetails = Some(baseBankDetails),
      protectedBankDetails = Some(ProtectedBankDetails(
        Some(SensitiveHasUKBankAccount(Some(true))),
        Some(SensitiveAccountName(Some("encryptedName"))),
        Some(SensitiveAccountNumber(Some("encryptedNumber"))),
        Some(SensitiveSortCode(Some(SortCode("99", "99", "99")))),
        Some(SensitiveBicSwiftCode(Some(BicSwiftCode("12345678901")))),
        Some(SensitiveIban(Some(Iban("encryptedIBAN"))))
      ))
    )

    lazy val existingPropertyDetails: PropertyDetails = PropertyDetails(atedRefNo = atedRefNo,
      id = propertyId,
      bankDetails = Some(bankDetailsModelWithAllFields),
      periodKey = periodKey2017,
      addressProperty = PropertyDetailsAddress("", "", Some(""), Some("")))

    "create bankDetails if not present" in new Setup {
      val noBankDetailsProp: PropertyDetails = PropertyDetails(atedRefNo = atedRefNo,
        id = propertyId,
        bankDetails = None,
        periodKey = periodKey2017,
        addressProperty = PropertyDetailsAddress("", "", Some(""), Some("")))

      when(mockPropertyDetailsCache.fetchPropertyDetails(atedRefNo))
        .thenReturn(Future.successful(Seq(noBankDetailsProp)))
      when(mockPropertyDetailsCache.cachePropertyDetails(any()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftHasUkBankAccount(atedRefNo, propertyId, hasUKBankAccount = true))

      result mustBe defined

      val bankDetails: BankDetails = result.get.bankDetails.get.bankDetails.get

      bankDetails.hasUKBankAccount mustBe Some(true)
      bankDetails.accountName mustBe None
      result.get.bankDetails.get.protectedBankDetails mustBe None
    }

    "preserve existing details if hasUKBankAccount unchanged" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(atedRefNo))
        .thenReturn(Future.successful(Seq(existingPropertyDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(any()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftHasUkBankAccount(atedRefNo, propertyId, hasUKBankAccount = true))

      result mustBe defined

      val updated: BankDetails = result.get.bankDetails.get.bankDetails.get

      updated.hasUKBankAccount mustBe Some(true)
      updated.accountName mustBe Some("encryptedName")
      updated.accountNumber mustBe Some("encryptedNumber")
      updated.sortCode mustBe Some(SortCode("99", "99", "99"))
      updated.bicSwiftCode mustBe Some(BicSwiftCode("12345678901"))
      updated.iban mustBe Some(Iban("encryptedIBAN"))
      result.get.bankDetails.get.protectedBankDetails mustBe None

    }

    "clear sensitive fields if hasUKBankAccount changed" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(atedRefNo))
        .thenReturn(Future.successful(Seq(existingPropertyDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(any()))
        .thenReturn(Future.successful(PropertyDetailsCached))

      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftHasUkBankAccount(atedRefNo, propertyId, hasUKBankAccount = false))

      result mustBe defined

      val updated: BankDetails = result.get.bankDetails.get.bankDetails.get

      updated.hasUKBankAccount mustBe Some(false)
      updated.accountName mustBe None
      updated.accountNumber mustBe None
      updated.sortCode mustBe None
      updated.bicSwiftCode mustBe None
      updated.iban mustBe None
      result.get.bankDetails.get.protectedBankDetails mustBe None

    }

    "return None if property ID not found" in new Setup {
      val otherProperty: PropertyDetails = existingPropertyDetails.copy(id = "some-other-id")

      when(mockPropertyDetailsCache.fetchPropertyDetails(atedRefNo))
        .thenReturn(Future.successful(Seq(otherProperty)))

      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftHasUkBankAccount(atedRefNo, propertyId, hasUKBankAccount = true))

      result mustBe None
    }
  }

  "cacheDraftBankDetails" must {
    lazy val protectedBankDetails: BankDetailsModel = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
    val periodKey2016: Int = 2016
    lazy val propertyDetailsWithBankDetails: PropertyDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(
      periodKey = periodKey2016,
      formBundle = "1",
      bankDetails = Some(protectedBankDetails)
    )

    lazy val propertyDetailsNoBankDetails: PropertyDetails = ChangeLiabilityReturnBuilder.generateChangeLiabilityReturn(
      periodKey = periodKey2016,
      formBundle = "1",
      bankDetails = None
    )

    "return Some(PropertyDetails) if form-bundle found in cache with bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsWithBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val bankDetails: BankDetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
      //val expected = updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle1, protectedBankDetails)
      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, propertyDetailsWithBankDetails.id, bankDetails))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails must be(Some(bankDetails))
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return Some(PropertyDetails) if form-bundle found in cache with no bank details" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Seq(propertyDetailsNoBankDetails)))
      when(mockPropertyDetailsCache.cachePropertyDetails(ArgumentMatchers.any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
      val bankDetails: BankDetails = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails.bankDetails.get
      //val expected = updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle1, protectedBankDetails)
      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, propertyDetailsNoBankDetails.id, bankDetails))

      result.get.bankDetails.get.hasBankDetails must be(true)
      result.get.bankDetails.get.bankDetails must be(Some(bankDetails))
      result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
    }

    "return None if form-bundle not-found in cache" in new Setup {
      when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(accountRef)))
        .thenReturn(Future.successful(Nil))
      val bankDetails: BankDetailsModel = ChangeLiabilityReturnBuilder.generateLiabilityBankDetails
      val result: Option[PropertyDetails] = await(testPropertyDetailsService.cacheDraftBankDetails(accountRef, "", bankDetails.bankDetails.get))
      result must be(None)
    }
  }


  "Retrieve the Liability Amount for the PropertyDetails" must {
    "Get the Liability Amount " in new Setup {
      lazy val propertyDetailsExample: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val successResponse: JsValue = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef),
        ArgumentMatchers.any[SubmitEtmpReturnsRequest]
      )(ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))

      val result: Future[Option[BigDecimal]] = testPropertyDetailsService.getLiabilityAmount(accountRef, "1", propertyDetailsExample)

      val liabilityAmount: Option[BigDecimal] = await(result)
      liabilityAmount must be(Some(999.99))
    }

    "Return None if we have no Liability Amount " in new Setup {
      lazy val propertyDetailsExample: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val successResponse: JsValue = Json.parse(jsonEtmpResponse)
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponse, Map.empty[String, Seq[String]])))
      val result: Future[Option[BigDecimal]] = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val liabilityAmount: Option[BigDecimal] = await(result)
      liabilityAmount.isDefined must be(false)
    }

    "Fail if we have BAD_REQUEST" in new Setup {
      lazy val propertyDetailsExample: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val failureResponse: JsValue = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef),
        ArgumentMatchers.any[SubmitEtmpReturnsRequest]
      )(ArgumentMatchers.any(), ArgumentMatchers.any())
      ).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, failureResponse, Map.empty[String, Seq[String]])))

      val result: Future[Option[BigDecimal]] = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val thrown: BadRequestException = the[BadRequestException] thrownBy await(result)
      thrown.getMessage must include("Error")
    }

    "Fail if we have dont find Liability Amount" in new Setup {
      lazy val propertyDetailsExample: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))

      val failureResponse: JsValue = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, failureResponse, Map.empty[String, Seq[String]])))
      val result: Future[Option[BigDecimal]] = testPropertyDetailsService.getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      val thrown: InternalServerException = the[InternalServerException] thrownBy await(result)
      thrown.getMessage must include("No Liability Amount Found")
      }

    "Fail if we have dont have valid details " in new Setup {
      lazy val propertyDetailsPopulated: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something better"))
      val propertyDetailsExample: PropertyDetails = propertyDetailsPopulated.copy(period = None, calculated = None)

      val failureResponse: JsValue = Json.parse( """{ "reason": "Error"}""")
      when(mockEtmpConnector.submitReturns(
        ArgumentMatchers.eq(accountRef), ArgumentMatchers.any[SubmitEtmpReturnsRequest])(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, failureResponse, Map.empty[String, Seq[String]])))
      val thrown: InternalServerException = the[InternalServerException]thrownBy testPropertyDetailsService
        .getLiabilityAmount(accountRef, "3", propertyDetailsExample)

      thrown.getMessage must include("Invalid Data for the request")
    }
  }

  "Submit the Property Details from the Cache" must {
    "Submit the property details and delete the item from the cache if it's a valid id" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"), liabilityAmount = Some(BigDecimal(999.99)))
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      type Retrieval = Option[Name]
      val testEnrolments: Set[Enrolment] = Set(Enrolment("HMRC-ATED-ORG", Seq(EnrolmentIdentifier("AgentRefNumber", "XN1200000100001")), "activated"))
      val name: Name = Name(Some("gary"),Some("bloggs"))
      val enrolmentsWithName: Retrieval = Some(name)

      val successResponse: JsValue = Json.parse(jsonEtmpResponse)
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
      when(mockSubscriptionDataService.retrieveSubscriptionData(
        ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
      when(mockEmailConnector.sendTemplatedEmail(
        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())) thenReturn Future.successful(EmailSent)

      val result: Future[HttpResponse] = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "1")
      await(result).status must be(OK)
      verify(mockEmailConnector, times(1)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }

    "return a NOT_FOUND if the property details doesn't exist for this id" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something"))
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetails1, propertyDetails2, propertyDetails3)))
      mockRetrievingNoAuthRef()
      when(mockSubscriptionDataService.retrieveSubscriptionData(
        ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

      val result: Future[HttpResponse] = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "4")
      await(result).status must be(NOT_FOUND)
      verify(mockEmailConnector, times(0)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }

    "return a NOT_FOUND if the property details are invalid for this id" in new Setup {
      lazy val propertyDetails1: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("1", Some("something")).copy(period = None, calculated = None)
      lazy val propertyDetails2: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("2", Some("something else"))
      lazy val propertyDetails3: PropertyDetails = PropertyDetailsBuilder.getPropertyDetails("3", Some("something more"))
      lazy val propertyDetailsExample: PropertyDetails = propertyDetails1.copy(period = None)

      when(mockPropertyDetailsCache.fetchPropertyDetails(accountRef))
        .thenReturn(Future.successful(List(propertyDetailsExample, propertyDetails2, propertyDetails3)))
      mockRetrievingNoAuthRef()
      when(mockSubscriptionDataService.retrieveSubscriptionData(
        ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

      val result: Future[HttpResponse] = testPropertyDetailsService.submitDraftPropertyDetail(accountRef, "1")
      await(result).status must be(NOT_FOUND)
      verify(mockEmailConnector, times(0)).sendTemplatedEmail(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }
  }

}
