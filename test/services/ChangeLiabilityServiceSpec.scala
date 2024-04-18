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

import builders.AuthFunctionalityHelper
import builders.ChangeLiabilityReturnBuilder._
import connectors.{EmailConnector, EmailSent, EtmpReturnsConnector}
import models._
import java.time.{ZoneId, ZonedDateTime}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import repository.{PropertyDetailsCached, PropertyDetailsDeleted, PropertyDetailsMongoRepository}
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class ChangeLiabilityServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {

  val mockPropertyDetailsCache: PropertyDetailsMongoRepository = mock[PropertyDetailsMongoRepository]
  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService: SubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  implicit val mockServicesConfig: ServicesConfig = mock[ServicesConfig]

  trait Setup {

    class TestChangeLiabilityReturnService extends ChangeLiabilityService {
      override implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      override val propertyDetailsCache: PropertyDetailsMongoRepository = mockPropertyDetailsCache
      override val etmpConnector: EtmpReturnsConnector = mockEtmpConnector
      override val authConnector: AuthConnector = mockAuthConnector
      override val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
      override val emailConnector: EmailConnector = mockEmailConnector
    }

    val testChangeLiabilityReturnService: TestChangeLiabilityReturnService = new TestChangeLiabilityReturnService()
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val successResponseJson: JsValue = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")
  val atedRefNo = "ated-ref-123"

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val formBundle3 = "100000000000"


  override def beforeEach(): Unit = {
    reset(mockPropertyDetailsCache)
    reset(mockAuthConnector)
    reset(mockEtmpConnector)
    reset(mockSubscriptionDataService)
    reset(mockEmailConnector)
  }


  "ChangeLiabilityService" must {

    lazy val changeLiability1 = generateChangeLiabilityReturn(2015, formBundle1)
    lazy val changeLiability2 = generateChangeLiabilityReturn(2015, formBundle2)
    lazy val changeLiability3 = generateChangeLiabilityReturnWithLiabilty(2015, formBundle1)

    val formBundleResponse1 = generateFormBundleResponse(2015)

    "convertSubmittedReturnToCachedDraft" must {
      "return Some(ChangeLiabilityReturn) if form-bundle found in cache" in new Setup {
        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(changeLiability1, changeLiability2)))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))
        result must be(Some(changeLiability1))
      }

      "return Some(ChangeLiabilityReturn) with protected bank details if form-bundle found in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle3, generateLiabilityProtectedBankDetails).copy(timeStamp = ZonedDateTime.of(2005, 3, 26, 12, 0, 0, 0, ZoneId.of("UTC"))))))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle3))
        result must be(Some(updateChangeLiabilityReturnWithBankDetails(2015, formBundle3, generateLiabilityBankDetails).copy(timeStamp = ZonedDateTime.of(2005, 3, 26, 12, 0, 0, 0, ZoneId.of("UTC")))))
      }

      "return Some(ChangeLiabilityReturn) if form-bundle not-found in cache, but found in ETMP - also cache it in mongo - based on previous return" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Json.toJson(formBundleResponse1), Map.empty[String, Seq[String]])))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1, Some(true), Some(2016)))

        result.get.title.isDefined must be(true)
        result.get.calculated.isDefined must be(false)
        result.get.formBundleReturn.isDefined must be(true)
        result.get.value.isDefined must be(true)
        result.get.period.isDefined must be(false)
        result.get.bankDetails.isDefined must be(false)
        result.get.periodKey must be(2016)
        result.get.id mustNot be(formBundle1)
      }


      "return Some(ChangeLiabilityReturn) if form-bundle not-found in cache, but found in ETMP - also cache it in mongo" in new Setup {
        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector
          .getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, Json.toJson(formBundleResponse1), Map.empty[String, Seq[String]])))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))

        result.get.title.isDefined must be(true)
        result.get.calculated.isDefined must be(false)
        result.get.formBundleReturn.isDefined must be(true)
        result.get.value.isDefined must be(true)
        result.get.period.isDefined must be(true)
        result.get.bankDetails.isDefined must be(false)
        result.get.periodKey must be(2015)
        result.get.id must be(formBundle1)

      }

      "return None if form-bundle not-found in cache as well as in ETMP" in new Setup {
        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector
          .getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "calculateDraftChangeLiability" must {
      "throw an exception, when there is no calculated object" in new Setup {
        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(changeLiability1)))
         when(mockPropertyDetailsCache
           .cachePropertyDetails(any[PropertyDetails]()))
           .thenReturn(Future.successful(PropertyDetailsCached))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(
            ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])) )
        mockRetrievingNoAuthRef()
        val thrown: NoLiabilityAmountException = the[NoLiabilityAmountException] thrownBy await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        thrown.message must include("[ChangeLiabilityService][getAmountDueOrRefund] Invalid Data for the request")
      }

      "calculate the change liabilty details, when calculated object is present" in new Setup {
        mockRetrievingNoAuthRef()

        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(changeLiability3)))
        when(mockPropertyDetailsCache
          .cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(
            ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))

        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        result.isDefined must be(true)
        result.get.title must be(Some(PropertyDetailsTitle("12345678")))
        result.get.calculated.isDefined must be(true)
        result.get.calculated.get.liabilityAmount must be(Some(BigDecimal(2000.00)))
        result.get.calculated.get.amountDueOrRefund must be(Some(-500.0))
      }

      "return None, if form-bundle not-found in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Nil))
        mockRetrievingNoAuthRef()
        when(mockPropertyDetailsCache.cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        val result: Option[PropertyDetails] = await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "emailTemplate" must {
      val liability = EditLiabilityReturnsResponse("Post", "12345", Some("1234567890123"), BigDecimal(2000.00), amountDueOrRefund = BigDecimal(500.00), paymentReference = Some("payment-ref-123"))
      "return further_return_submit when amountDueOrRefund is greater than 0" in new Setup {
        val json: JsValue = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = ZonedDateTime.parse("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("further_return_submit")
      }
      "return amended_return_submit when amountDueOrRefund is less than 0" in new Setup {
        val json: JsValue = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = ZonedDateTime.parse("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability.copy(amountDueOrRefund = BigDecimal(-500.00))), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("amended_return_submit")
      }
      "return change_details_return_submit when amountDueOrRefund is 0" in new Setup {
        val json: JsValue = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = ZonedDateTime.parse("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability.copy(amountDueOrRefund = BigDecimal(0.00))), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("change_details_return_submit")
      }
    }

    "submitChangeLiability" must {
      "return status OK, if return found in cache and submitted correctly and then deleted from cache" in new Setup {
        val calc1: PropertyDetailsCalculated = generateCalculated
        val changeLiability1Changed: PropertyDetails = changeLiability1.copy(calculated = Some(calc1))

        type Retrieval = Option[Name]
        val testEnrolments: Set[Enrolment] = Set(Enrolment("HMRC-ATED-ORG", Seq(EnrolmentIdentifier("AgentRefNumber", "XN1200000100001")), "activated"))
        val name: Name = Name(Some("gary"),Some("bloggs"))
        val enrolmentsWithName: Retrieval = Some(name)

        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1Changed, changeLiability2)))
        when(mockAuthConnector
          .authorise[Any](any(), any())(any(), any())).thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
        when(mockPropertyDetailsCache
          .cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        when(mockPropertyDetailsCache.deletePropertyDetailsByfieldName(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(PropertyDetailsDeleted))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        when(mockEmailConnector.sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

        val result: HttpResponse = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(OK)
        verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status NOT_FOUND, if return not-found in cache and hence not-submitted correctly and then not-deleted from cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1)))
        mockRetrievingNoAuthRef()
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

        val result: HttpResponse = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle2))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status NOT_FOUND, if return found in cache but calculated values don't exist in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1)))
        mockRetrievingNoAuthRef()
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))

        val result: HttpResponse = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status returned by connector, if return found in cache and but submission failed and hence draft return is not-deleted from cache" in new Setup {
        val calc1: PropertyDetailsCalculated = generateCalculated
        val changeLiability1Changed: PropertyDetails = changeLiability1.copy(calculated = Some(calc1))
        when(mockPropertyDetailsCache
          .fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(changeLiability1Changed, changeLiability2)))

        mockRetrievingNoAuthRef()
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, respJson, Map.empty[String, Seq[String]])))
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: HttpResponse = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(BAD_REQUEST)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }
    }

    "getAmountDueOrRefund" must {
      "throw an NoLiabilityAmountException if due to some issue, the API call returned BAD_REQUEST (invalid data)" in new Setup {
        val calc1: PropertyDetailsCalculated = generateCalculated
        val changeLiability1WithCalc: PropertyDetails = changeLiability1.copy(calculated = Some(calc1))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, respJson, Map.empty[String, Seq[String]])))
        val thrown: NoLiabilityAmountException = the[NoLiabilityAmountException] thrownBy await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, "1", changeLiability1WithCalc))
        thrown.message must include("No Liability Amount Found")
      }

      "throw an InternalServerException if due to some issue, the API call didn't return OK as status" in new Setup {
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, respJson, Map.empty[String, Seq[String]])))
      }

      "throw an exception if calculated is not found in cache" in new Setup {
        val thrown: NoLiabilityAmountException = the[NoLiabilityAmountException]thrownBy await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, "1", changeLiability1))
        thrown.message must include("[ChangeLiabilityService][getAmountDueOrRefund] Invalid Data for the request")
      }


      "liability and amount due or refund is returned, if ETMP returns OK but " in new Setup {
        val calc1: PropertyDetailsCalculated = generateCalculated
        val changeLiability1WithCalc: PropertyDetails = changeLiability1.copy(calculated = Some(calc1))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: (Option[BigDecimal], Option[BigDecimal]) = await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, formBundle1, changeLiability1WithCalc))
        result must be((Some(2000.0), Some(-500.0)))
      }
    }

  }

}
