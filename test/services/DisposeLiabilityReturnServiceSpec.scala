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

import builders.ChangeLiabilityReturnBuilder._
import builders.{AuthFunctionalityHelper, ChangeLiabilityReturnBuilder}
import connectors.{EmailConnector, EmailSent, EtmpReturnsConnector}
import models._

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import repository.{DisposeLiabilityReturnCached, DisposeLiabilityReturnMongoRepository}
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class DisposeLiabilityReturnServiceSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach with AuthFunctionalityHelper {
  override val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockDisposeLiabilityReturnRepository: DisposeLiabilityReturnMongoRepository = mock[DisposeLiabilityReturnMongoRepository]
  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockSubscriptionDataService: SubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val mockNotificationService: NotificationService = mock[NotificationService]

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val atedRefNo = "ated-ref-123"
  val successResponseJson: JsValue = Json.parse("""{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val formBundle3 = "100000000000"
  val periodKey = 2015
  val formBundleReturn1: FormBundleReturn = generateFormBundleResponse(periodKey)
  val formBundleReturn2: FormBundleReturn = generateFormBundleResponse(periodKey)

  type Retrieval = Option[Name]
  val testEnrolments: Set[Enrolment] = Set(Enrolment("HMRC-ATED-ORG", Seq(EnrolmentIdentifier("AgentRefNumber", "XN1200000100001")), "activated"))
  val name: Name = Name(Some("gary"),Some("bloggs"))
  val enrolmentsWithName: Retrieval = Some(name)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def beforeEach(): Unit = {
    reset(mockDisposeLiabilityReturnRepository)
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
    reset(mockEmailConnector)
    reset(mockSubscriptionDataService)
  }

  trait Setup {

    val testDisposeLiabilityReturnService = new TestDisposeLiabilityReturnService()

    class TestDisposeLiabilityReturnService extends DisposeLiabilityReturnService {
      override val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      override val etmpReturnsConnector: EtmpReturnsConnector = mockEtmpConnector
      override val disposeLiabilityReturnRepository: DisposeLiabilityReturnMongoRepository = mockDisposeLiabilityReturnRepository
      override val authConnector: AuthConnector = mockAuthConnector
      override val subscriptionDataService: SubscriptionDataService = mockSubscriptionDataService
      override val emailConnector: EmailConnector = mockEmailConnector
    }
  }

  "DisposeLiabilityReturnService" must {

    lazy val disposeLiability1 = DisposeLiabilityReturn(atedRefNo = atedRefNo, formBundle1, formBundleReturn1)
    lazy val disposeLiability2 = DisposeLiabilityReturn(atedRefNo = atedRefNo, formBundle2, formBundleReturn2)

    "retrieveDraftDisposeLiabilityReturns" must {
      "return Seq[DisposeLiabilityReturn], as found in cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq[DisposeLiabilityReturn](disposeLiability1, disposeLiability2)))
        val result: Seq[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(atedRefNo))
        result.size must be(2)
      }
    }

    "retrieveDraftChangeLiabilityReturn" must {
      "return Some(DisposeLiabilityReturn) if form-bundle found" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1))
      }

      "return None if form-bundle not-found" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturn(atedRefNo, formBundle3))
        result must be(None)
      }
    }

    "retrieveAndCacheDisposeLiabilityReturn" must {
      "return cached DisposeLiabilityReturn, if found in mongo cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1))
      }

      "return cached DisposeLiabilityReturn with protected bank details, if found in mongo cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1.copy(bankDetails = Some(generateLiabilityProtectedBankDetails)), disposeLiability2)))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1.copy(bankDetails = Some(generateLiabilityBankDetails))))
      }

      "return DisposeLiabilityReturn, if not found in mongo, but found in ETMP call, also cache it in mongo for future calls" in new Setup {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector
          .getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1.toString))(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, Json.toJson(formBundleReturn1), Map.empty[String, Seq[String]])))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(None)
      }
      "return None, because neither dispose was found in mongo, nor there was any formBundle returned from ETMP" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockEtmpConnector
          .getFormBundleReturns(ArgumentMatchers.eq(atedRefNo), ArgumentMatchers.eq(formBundle1.toString))(any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "updateDraftDisposeLiabilityReturnDate" must {
      "update, cache and return DisposeLiabilityReturn with the dae of disposal, if found in cache" in new Setup {
        lazy val inputDisposeLiability: DisposeLiability = DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(atedRefNo, formBundle1, inputDisposeLiability))
        result must be(Some(disposeLiability1.copy(disposeLiability = Some(inputDisposeLiability))))
      }

      "return None, if not found in mongo cache" in new Setup {
        lazy val inputDisposeLiability: DisposeLiability = DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(atedRefNo, formBundle1, inputDisposeLiability))
        result must be(None)
      }
    }

    "updateDraftDisposeHasBankDetails" must {
      lazy val protectedBankDetails = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
      lazy val disposeLiability1WithBankDetails = disposeLiability1.copy(bankDetails = Some(protectedBankDetails))

      "create the bank details model if we have none" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, hasBankDetails = true))
        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
      }

      "keep the bank details if hasBankDetails is true" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1WithBankDetails, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, hasBankDetails = true))
        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(true)
      }

      "clear the bankDetails if hasBankDetails is false" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1WithBankDetails, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, hasBankDetails = false))
        result.get.bankDetails.get.hasBankDetails must be(false)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
      }

      "return None, if not found in mongo cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, hasBankDetails = true))
        result must be(None)
      }
    }

    "updateDraftDisposeBankDetails" must {
      "create bankDetails if we have none" in new Setup {
        lazy val bankDetails: BankDetailsModel = generateLiabilityBankDetails
        val dL1: DisposeLiabilityReturn = disposeLiability1
          .copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)), bankDetails = None)
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: Option[DisposeLiabilityReturn] = await(
          testDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bankDetails.bankDetails.get))

        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(true)
      }


      "update bankDetails and cache that into mongo" in new Setup {
        lazy val bankDetails: BankDetailsModel = generateLiabilityBankDetails
        lazy val protectedBankDetails: BankDetailsModel = generateLiabilityProtectedBankDetails
        val dL1: DisposeLiabilityReturn = disposeLiability1.copy(disposeLiability = Some(
          DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)), bankDetails = Some(protectedBankDetails))
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson: JsValue = Json.toJson(respModel)

        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: Option[DisposeLiabilityReturn] = await(
          testDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bankDetails.bankDetails.get)
        )

        val expected: DisposeLiabilityReturn = dL1.copy(calculated = None)
        result must be(Some(expected))
      }

      "return None, if form-bundle-no is not found in cache, in such case don't do pre-calculation call" in new Setup {
        lazy val bank1: BankDetailsModel = generateLiabilityBankDetails
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        val result: Option[DisposeLiabilityReturn] = await(
          testDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bank1.bankDetails.get)
        )
        result must be(None)
        verify(mockEtmpConnector, times(0)).submitEditedLiabilityReturns(any(), any(), any())(any(), any())
      }
    }

    "calculateDraftDispose" must {

      "update the pre calculated values and cache that into mongo" in new Setup {
        lazy val bankDetails: BankDetailsModel = generateLiabilityBankDetails
        lazy val protectedBankDetails: BankDetailsModel = generateLiabilityProtectedBankDetails
        val dL1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)), bankDetails = Some(protectedBankDetails))
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson: JsValue = Json.toJson(respModel)

        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))

        val expected: DisposeLiabilityReturn = dL1
          .copy(bankDetails = Some(bankDetails), calculated = Some(DisposeCalculated(BigDecimal(2000.00), BigDecimal(-500.00))))
        result must be(Some(expected))
      }

      "throw exception if pre-calculation call fails" in new Setup {

        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()

        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))
        val thrown: RuntimeException = the[RuntimeException] thrownBy await(testDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))

        thrown.getMessage must include("pre-calculation-request returned wrong status")
        verify(mockEtmpConnector, times(1)).submitEditedLiabilityReturns(any(), any(), any())(any(), any())
      }

      "return None, if form-bundle-no is not found in cache, in such case don't do pre-calculation call" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        val result: Option[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))
        result must be(None)
        verify(mockEtmpConnector, times(0)).submitEditedLiabilityReturns(any(), any(), any())(any(), any())
      }
    }

    "getPreCalculationAmounts" must {
      "just in case, if returned oldFornBundleReturnNo is not equal to one being passed, return amounts as 0,0" in new Setup {
        val bank1: BankDetailsModel = generateLiabilityBankDetails
        val dL1: DisposeLiabilityReturn = disposeLiability1
          .copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(LocalDate.of(2015, 5, 1)), periodKey = periodKey)), bankDetails = Some(bank1))
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: DisposeCalculated = await(testDisposeLiabilityReturnService.getPreCalculationAmounts(atedRefNo, formBundleReturn1,DisposeLiability(Some(LocalDate.of(2015, 5, 1)), periodKey), formBundle2))
        result must be(DisposeCalculated(BigDecimal(0.00), BigDecimal(0.00)))
      }
    }

    "deleteDisposeLiabilityDraft" must {
      "return Seq[DisposeLiabilityReturn] with removing the particular form-bundle-return, if form-bundle found in cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result: Seq[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.deleteDisposeLiabilityDraft(atedRefNo, formBundle1))
        result must be(Seq(disposeLiability2))
      }

      "return Nil if form-bundle not-found in cache" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Nil))
        val result: Seq[DisposeLiabilityReturn] = await(testDisposeLiabilityReturnService.deleteDisposeLiabilityDraft(atedRefNo, formBundle1))
        result must be(Seq())
      }
    }

    "submitDisposeLiability" must {
      "return HttpResponse wit Status OK, when form-bundle is found in cache and successfully submitted to ETMP" must {
        "getEtmpBankDetails" must {
          "return BankDetails, if valid bank-details-model is passed" in new Setup {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(
              DisposeLiability(Some(LocalDate.of(2015, 5, 1)), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))

            when(mockDisposeLiabilityReturnRepository
              .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
              .thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository
              .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
              .thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector
              .authorise[Any](any(), any())(any(), any()))
              .thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
            when(mockSubscriptionDataService
              .retrieveSubscriptionData(any())(any()))
              .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
            when(mockEmailConnector
              .sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)

            lazy val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson: JsValue = Json.toJson(respModel)
            when(mockEtmpConnector
              .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
              .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))

            val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
          }
          "return None, if hasBankDetails is false passed" in new Setup {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(
                Some(LocalDate.of(2015, 5, 1)), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder
              .generateLiabilityProtectedBankDetailsNoBankDetails), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))

            when(mockDisposeLiabilityReturnRepository
              .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
              .thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository
              .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
              .thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector
              .authorise[Any](any(), any())(any(), any()))
              .thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
            when(mockSubscriptionDataService
              .retrieveSubscriptionData(any())(any()))
              .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
            val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            val respJson: JsValue = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
              .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
            when(mockEmailConnector.sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
            val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
          }

          "return None, if accountNumber & accountName & sortCode is not found" in new Setup {
            lazy val disp1: DisposeLiabilityReturn = disposeLiability1
              .copy(disposeLiability = Some(
                DisposeLiability(Some(LocalDate.of(2015, 5, 1)), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsBlank), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository
              .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
              .thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository
              .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
              .thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector
              .authorise[Any](any(), any())(any(), any()))
              .thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
            when(mockSubscriptionDataService
              .retrieveSubscriptionData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
            when(mockEmailConnector
              .sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
            lazy val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson: JsValue = Json.toJson(respModel)
            when(mockEtmpConnector
              .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
              .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
            val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
          }

          "return None, if None was passed as bank-details-model" in new Setup {
            lazy val disp1 = disposeLiability1
              .copy(disposeLiability = Some(
                DisposeLiability(Some(LocalDate.of(2015, 5, 1)), periodKey)), calculated = Some( DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository
              .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository
              .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector
              .authorise[Any](any(), any())(any(), any())).thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
            when(mockSubscriptionDataService
              .retrieveSubscriptionData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
            when(mockEmailConnector
              .sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
            lazy val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson: JsValue = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
              .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
            val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
          }
        }
      }
      "generateEditReturnRequest - if dateOfDisposal is not found, use oldFormbundleReturn 'date from' value" in new Setup {
        lazy val bank1: BankDetailsModel = generateLiabilityBankDetails
        lazy val disp1: DisposeLiabilityReturn = disposeLiability1
          .copy(disposeLiability = Some(DisposeLiability(None, periodKey)), bankDetails = Some(bank1), calculated = Some(
            DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]()))
          .thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector
          .authorise[Any](any(), any())(any(), any()))
          .thenReturn(Future.successful(Enrolments(testEnrolments)), Future.successful(enrolmentsWithName))
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        when(mockEmailConnector
          .sendTemplatedEmail(any(), any(), any())(any())) thenReturn Future.successful(EmailSent)
        lazy val respModel: EditLiabilityReturnsResponseModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        lazy val respJson: JsValue = Json.toJson(respModel)
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(OK, respJson, Map.empty[String, Seq[String]])))
        val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(OK)
        verify(mockEmailConnector, times(1)).sendTemplatedEmail(any(), any(), any())(any())
      }
      "return NOT_FOUND as status, if form-bundle not found in list" in new Setup {
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability2)))
        mockRetrievingNoAuthRef()
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }
      "return the status with body, if etmp call returns any other status other than OK" in new Setup {
        lazy val bank1: BankDetailsModel = generateLiabilityBankDetails
        lazy val disp1: DisposeLiabilityReturn = disposeLiability1
          .copy(disposeLiability = Some(DisposeLiability(Some(LocalDate
            .of(2015, 5, 1)), periodKey)), bankDetails = Some(bank1), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
        when(mockDisposeLiabilityReturnRepository
          .fetchDisposeLiabilityReturns(ArgumentMatchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository
          .cacheDisposeLiabilityReturns(any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        mockRetrievingNoAuthRef()
        when(mockSubscriptionDataService
          .retrieveSubscriptionData(any())(any()))
          .thenReturn(Future.successful(HttpResponse(OK, successResponseJson, Map.empty[String, Seq[String]])))
        when(mockEtmpConnector
          .submitEditedLiabilityReturns(ArgumentMatchers.eq(atedRefNo), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, Json.parse("""{"reason": "Server error"}"""), Map.empty[String, Seq[String]])))
        val result: HttpResponse = await(testDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(INTERNAL_SERVER_ERROR)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(any(), any(), any())(any())
      }
    }
  }

}
