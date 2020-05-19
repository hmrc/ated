/*
 * Copyright 2020 HM Revenue & Customs
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

import builders.ChangeLiabilityReturnBuilder._
import builders.{AuthFunctionalityHelper, PropertyDetailsBuilder}
import connectors.{EmailConnector, EmailSent, EtmpReturnsConnector}
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import reactivemongo.api.commands.WriteResult
import repository.{PropertyDetailsCached, PropertyDetailsMongoRepository}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, InternalServerException}
import uk.gov.hmrc.mongo.DatabaseUpdate

import scala.concurrent.Future

class ChangeLiabilityServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {

  val mockPropertyDetailsCache = mock[PropertyDetailsMongoRepository]
  val mockWriteResult = mock[WriteResult]
  val mockDatabaseUpdate = mock[DatabaseUpdate[Cache]]
  val mockEtmpConnector = mock[EtmpReturnsConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector = mock[EmailConnector]

  trait Setup {

    class TestChangeLiabilityReturnService extends ChangeLiabilityService {
      override val propertyDetailsCache = mockPropertyDetailsCache
      override val etmpConnector = mockEtmpConnector
      override val authConnector = mockAuthConnector
      override val subscriptionDataService = mockSubscriptionDataService
      override val emailConnector = mockEmailConnector
    }

    val testChangeLiabilityReturnService: TestChangeLiabilityReturnService = new TestChangeLiabilityReturnService()
  }

  val mockedDatabaseUpdate = mock[DatabaseUpdate[Cache]]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val successResponseJson = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")
  val atedRefNo = "ated-ref-123"

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val formBundle3 = "100000000000"


  override def beforeEach = {
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
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(changeLiability1, changeLiability2)))
        val result = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))
        result must be(Some(changeLiability1))
      }

      "return Some(ChangeLiabilityReturn) with protected bank details if form-bundle found in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(updateChangeLiabilityReturnWithProtectedBankDetails(2015, formBundle3, generateLiabilityProtectedBankDetails).copy(timeStamp = new DateTime(2005, 3, 26, 12, 0, 0, 0)))))
        val result = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle3))
        result must be(Some(updateChangeLiabilityReturnWithBankDetails(2015, formBundle3, generateLiabilityBankDetails).copy(timeStamp = new DateTime(2005, 3, 26, 12, 0, 0, 0))))
      }

      "return Some(ChangeLiabilityReturn) if form-bundle not-found in cache, but found in ETMP - also cache it in mongo - based on previous return" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1.toString))).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson(formBundleResponse1)))))
        val result = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1, Some(true), Some(2016)))

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
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1.toString))).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson(formBundleResponse1)))))
        val result = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))

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
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1))).thenReturn(Future.successful(HttpResponse(NOT_FOUND, responseJson = None)))
        val result = await(testChangeLiabilityReturnService.convertSubmittedReturnToCachedDraft(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "calculateDraftChangeLiability" must {
      "throw an exception, when there is no calculated object" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1)))
         when(mockPropertyDetailsCache.cachePropertyDetails(any[PropertyDetails]()))
        .thenReturn(Future.successful(PropertyDetailsCached))
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        mockRetrievingNoAuthRef
        val thrown = the[InternalServerException] thrownBy await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        thrown.getMessage must include("[ChangeLiabilityService][getAmountDueOrRefund] Invalid Data for the request")
      }

      "calculate the change liabilty details, when calculated object is present" in new Setup {
        mockRetrievingNoAuthRef

        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability3)))
        when(mockPropertyDetailsCache.cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))

        val result = await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        result.isDefined must be(true)
        result.get.title must be(Some(PropertyDetailsTitle("12345678")))
        result.get.calculated.isDefined must be(true)
        result.get.calculated.get.liabilityAmount must be(Some(BigDecimal(2000.00)))
        result.get.calculated.get.amountDueOrRefund must be(Some(-500.0))
      }

      "return None, if form-bundle not-found in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Nil))
        mockRetrievingNoAuthRef
        val periodDetails = PropertyDetailsBuilder.getPropertyDetailsPeriodFull(2015).get
        when(mockPropertyDetailsCache.cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        val result = await(testChangeLiabilityReturnService.calculateDraftChangeLiability(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "emailTemplate" must {
      val liability = EditLiabilityReturnsResponse("Post", "12345", Some("1234567890123"), BigDecimal(2000.00), amountDueOrRefund = BigDecimal(500.00), paymentReference = Some("payment-ref-123"))
      "return further_return_submit when amountDueOrRefund is greater than 0" in new Setup {
        val json = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = new DateTime("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("further_return_submit")
      }
      "return amended_return_submit when amountDueOrRefund is less than 0" in new Setup {
        val json = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = new DateTime("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability.copy(amountDueOrRefund = BigDecimal(-500.00))), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("amended_return_submit")
      }
      "return change_details_return_submit when amountDueOrRefund is 0" in new Setup {
        val json = Json.toJson(EditLiabilityReturnsResponseModel(processingDate = new DateTime("2016-04-20T12:41:41.839+01:00"), liabilityReturnResponse = Seq(liability.copy(amountDueOrRefund = BigDecimal(0.00))), accountBalance = BigDecimal(1200.00)))
        testChangeLiabilityReturnService.emailTemplate(json, "12345") must be("change_details_return_submit")
      }
    }

    "submitChangeLiability" must {
      "return status OK, if return found in cache and submitted correctly and then deleted from cache" in new Setup {
        val calc1 = generateCalculated
        val changeLiability1Changed = changeLiability1.copy(calculated = Some(calc1))
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1Changed, changeLiability2)))
        mockRetrievingNoAuthRef
        when(mockPropertyDetailsCache.cachePropertyDetails(any[PropertyDetails]()))
          .thenReturn(Future.successful(PropertyDetailsCached))
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        when(mockSubscriptionDataService.retrieveSubscriptionData(any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        when(mockEmailConnector.sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

        val result = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(OK)
        verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status NOT_FOUND, if return not-found in cache and hence not-submitted correctly and then not-deleted from cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1)))
        mockRetrievingNoAuthRef
        when(mockSubscriptionDataService.retrieveSubscriptionData(any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle2))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status NOT_FOUND, if return found in cache but calculated values don't exist in cache" in new Setup {
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1)))
        mockRetrievingNoAuthRef
        when(mockSubscriptionDataService.retrieveSubscriptionData(any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }

      "return status returned by connector, if return found in cache and but submission failed and hence draft return is not-deleted from cache" in new Setup {
        val calc1 = generateCalculated
        val changeLiability1Changed = changeLiability1.copy(calculated = Some(calc1))
        when(mockPropertyDetailsCache.fetchPropertyDetails(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(changeLiability1Changed, changeLiability2)))
        mockRetrievingNoAuthRef
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(respJson))))
        when(mockSubscriptionDataService.retrieveSubscriptionData(any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = await(testChangeLiabilityReturnService.submitChangeLiability(atedRefNo, formBundle1))
        result.status must be(BAD_REQUEST)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }
    }

    "getAmountDueOrRefund" must {
      "throw an NoLiabilityAmountException if due to some issue, the API call returned BAD_REQUEST (invalid data)" in new Setup {
        val calc1 = generateCalculated
        val changeLiability1WithCalc = changeLiability1.copy(calculated = Some(calc1))
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any()))
          .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, responseJson = Some(respJson))))
        val thrown = the[NoLiabilityAmountException] thrownBy await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, "1", changeLiability1WithCalc))
        thrown.message must include("No Liability Amount Found")
      }

      "throw an InternalServerException if due to some issue, the API call didn't return OK as status" in new Setup {
        val calc1 = generateCalculated
        val changeLiability1WithCalc = changeLiability1.copy(calculated = Some(calc1))
        val respModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(respJson))))
        val thrown = the[InternalServerException] thrownBy await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, "1", changeLiability1WithCalc))
      }

      "throw an exception if calculated is not found in cache" in new Setup {
        val thrown = the[InternalServerException] thrownBy await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, "1", changeLiability1))
        thrown.getMessage must include("Invalid Data for the request")
      }


      "liability and amount due or refund is returned, if ETMP returns OK but " in new Setup {
        val calc1: PropertyDetailsCalculated = generateCalculated
        val changeLiability1WithCalc: PropertyDetails = changeLiability1.copy(calculated = Some(calc1))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result: (Option[BigDecimal], Option[BigDecimal]) = await(testChangeLiabilityReturnService.getAmountDueOrRefund(atedRefNo, formBundle1, changeLiability1WithCalc))
        result must be(Some(2000.0), Some(-500.0))
      }
    }

  }

}
