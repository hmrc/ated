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

import builders.ChangeLiabilityReturnBuilder
import builders.ChangeLiabilityReturnBuilder._
import connectors.{AuthConnector, EmailConnector, EmailSent, EtmpReturnsConnector}
import models._
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import repository.{DisposeLiabilityReturnCached, DisposeLiabilityReturnMongoRepository}
import uk.gov.hmrc.mongo.DatabaseUpdate

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class DisposeLiabilityReturnServiceSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  val mockDisposeLiabilityReturnRepository: DisposeLiabilityReturnMongoRepository = mock[DisposeLiabilityReturnMongoRepository]
  val mockEtmpConnector: EtmpReturnsConnector = mock[EtmpReturnsConnector]
  val mockedDatabaseUpdate = mock[DatabaseUpdate[Cache]]
  val mockAuthConnector = mock[AuthConnector]
  val mockSubscriptionDataService = mock[SubscriptionDataService]
  val mockEmailConnector = mock[EmailConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val atedRefNo = "ated-ref-123"
  val successResponseJson = Json.parse( """{"safeId":"1111111111","organisationName":"Test Org","address":[{"name1":"name1","name2":"name2","addressDetails":{"addressType":"Correspondence","addressLine1":"address line 1","addressLine2":"address line 2","addressLine3":"address line 3","addressLine4":"address line 4","postalCode":"ZZ1 1ZZ","countryCode":"GB"},"contactDetails":{"phoneNumber":"01234567890","mobileNumber":"0712345678","emailAddress":"test@mail.com"}}]}""")

  val formBundle1 = "123456789012"
  val formBundle2 = "123456789000"
  val formBundle3 = "100000000000"
  val periodKey = 2015
  val formBundleReturn1 = generateFormBundleResponse(periodKey)
  val formBundleReturn2 = generateFormBundleResponse(periodKey)

  object TestDisposeLiabilityReturnService extends DisposeLiabilityReturnService {
    override val etmpReturnsConnector = mockEtmpConnector
    override val disposeLiabilityReturnRepository = mockDisposeLiabilityReturnRepository
    override val authConnector = mockAuthConnector
    override val subscriptionDataService = mockSubscriptionDataService
    override val emailConnector = mockEmailConnector
  }

  override def beforeEach = {
    reset(mockDisposeLiabilityReturnRepository)
    reset(mockEtmpConnector)
    reset(mockAuthConnector)
    reset(mockEmailConnector)
    reset(mockSubscriptionDataService)
  }

  "DisposeLiabilityReturnService" must {

    lazy val disposeLiability1 = DisposeLiabilityReturn(atedRefNo = atedRefNo, formBundle1, formBundleReturn1)
    lazy val disposeLiability2 = DisposeLiabilityReturn(atedRefNo = atedRefNo, formBundle2, formBundleReturn2)

    "use correct ETMP connector" in {
      DisposeLiabilityReturnService.etmpReturnsConnector must be(EtmpReturnsConnector)
    }

    "use Correct repository" in {
      DisposeLiabilityReturnService.disposeLiabilityReturnRepository must be(DisposeLiabilityReturnMongoRepository())
    }

    "retrieveDraftDisposeLiabilityReturns" must {
      "return Seq[DisposeLiabilityReturn], as found in cache" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq[DisposeLiabilityReturn](disposeLiability1, disposeLiability2)))
        val result = await(TestDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturns(atedRefNo))
        result.size must be(2)
      }
    }

    "retrieveDraftChangeLiabilityReturn" must {
      "return Some(DisposeLiabilityReturn) if form-bundle found" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result = await(TestDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1))
      }

      "return None if form-bundle not-found" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result = await(TestDisposeLiabilityReturnService.retrieveDraftDisposeLiabilityReturn(atedRefNo, formBundle3))
        result must be(None)
      }
    }

    "retrieveAndCacheDisposeLiabilityReturn" must {
      "return cached DisposeLiabilityReturn, if found in mongo cache" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        val result = await(TestDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1))
      }

      "return cached DisposeLiabilityReturn with protected bank details, if found in mongo cache" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1.copy(bankDetails = Some(generateLiabilityProtectedBankDetails)), disposeLiability2)))
        val result = await(TestDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(Some(disposeLiability1.copy(bankDetails = Some(generateLiabilityBankDetails))))
      }

      "return DisposeLiabilityReturn, if not found in mongo, but found in ETMP call, also cache it in mongo for future calls" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq()))
        when(mockEtmpConnector.getFormBundleReturns(Matchers.eq(atedRefNo), Matchers.eq(formBundle1.toString))).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(Json.toJson(formBundleReturn1)))))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be (None)
      }
      "return None, because neither dispose was found in mongo, nor there was any formBundle returned from ETMP" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockEtmpConnector.getFormBundleReturns(Matchers.eq(atedRefNo), Matchers.eq(formBundle1.toString))).thenReturn(Future.successful(HttpResponse(NOT_FOUND)))
        val result = await(TestDisposeLiabilityReturnService.retrieveAndCacheDisposeLiabilityReturn(atedRefNo, formBundle1))
        result must be(None)
      }
    }

    "updateDraftDisposeLiabilityReturnDate" must {
      "update, cache and return DisposeLiabilityReturn with the dae of disposal, if found in cache" in {
        lazy val inputDisposeLiability = DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(atedRefNo, formBundle1, inputDisposeLiability))
        result must be(Some(disposeLiability1.copy(disposeLiability = Some(inputDisposeLiability))))
      }

      "return None, if not found in mongo cache" in {
        lazy val inputDisposeLiability = DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeLiabilityReturnDate(atedRefNo, formBundle1, inputDisposeLiability))
        result must be(None)
      }
    }

    "updateDraftDisposeHasBankDetails" must {
      lazy val protectedBankDetails = ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails
      lazy val disposeLiability1WithBankDetails = disposeLiability1.copy(bankDetails = Some(protectedBankDetails))

      "create the bank details model if we have none" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, true))
        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
      }

      "keep the bank details if hasBankDetails is true" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1WithBankDetails, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, true))
        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(true)
      }

      "clear the bankDetails if hasBankDetails is false" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1WithBankDetails, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, false))
        result.get.bankDetails.get.hasBankDetails must be(false)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(false)
      }

      "return None, if not found in mongo cache" in {
        lazy val inputDisposeLiability = DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeHasBankDetails(atedRefNo, formBundle1, true))
        result must be(None)
      }
    }

    "updateDraftDisposeBankDetails" must {
      "create bankDetails if we have none" in {
        lazy val bankDetails = generateLiabilityBankDetails
        lazy val protectedBankDetails = generateLiabilityProtectedBankDetails
        val dL1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)), bankDetails = None)
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bankDetails.bankDetails.get))
        val expected = dL1.copy(calculated = Some(DisposeCalculated(BigDecimal(2000.00), BigDecimal(-500.00))))

        result.get.bankDetails.get.hasBankDetails must be(true)
        result.get.bankDetails.get.bankDetails.isDefined must be(false)
        result.get.bankDetails.get.protectedBankDetails.isDefined must be(true)
      }


      "update bankDetails and cache that into mongo" in {
        lazy val bankDetails = generateLiabilityBankDetails
        lazy val protectedBankDetails = generateLiabilityProtectedBankDetails
        val dL1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)), bankDetails = Some(protectedBankDetails))
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)

        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bankDetails.bankDetails.get))

        val expected = dL1.copy(calculated = None)
        result must be(Some(expected))
      }

      "return None, if form-bundle-no is not found in cache, in such case don't do pre-calculation call" in {
        lazy val bank1 = generateLiabilityBankDetails
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)
        val result = await(TestDisposeLiabilityReturnService.updateDraftDisposeBankDetails(atedRefNo, formBundle1, bank1.bankDetails.get))
        result must be(None)
        verify(mockEtmpConnector, times(0)).submitEditedLiabilityReturns(Matchers.any(), Matchers.any(), Matchers.any())
      }
    }

    "calculateDraftDispose" must {

      "update the pre calculated values and cache that into mongo" in {
        lazy val bankDetails = generateLiabilityBankDetails
        lazy val protectedBankDetails = generateLiabilityProtectedBankDetails
        val dL1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)), bankDetails = Some(protectedBankDetails))
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)

        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result = await(TestDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))

        val expected = dL1.copy(bankDetails = Some(bankDetails), calculated = Some(DisposeCalculated(BigDecimal(2000.00), BigDecimal(-500.00))))
        result must be(Some(expected))
      }

      "throw exception if pre-calculation call fails" in {

        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))

        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))
        val thrown = the[RuntimeException] thrownBy await(TestDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))

        thrown.getMessage must include("pre-calculation-request returned wrong status")
        verify(mockEtmpConnector, times(1)).submitEditedLiabilityReturns(Matchers.any(), Matchers.any(), Matchers.any())
      }

      "return None, if form-bundle-no is not found in cache, in such case don't do pre-calculation call" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)
        val result = await(TestDisposeLiabilityReturnService.calculateDraftDispose(atedRefNo, formBundle1))
        result must be(None)
        verify(mockEtmpConnector, times(0)).submitEditedLiabilityReturns(Matchers.any(), Matchers.any(), Matchers.any())
      }
    }

    "getPreCalculationAmounts" must {
      "just in case, if returned oldFornBundleReturnNo is not equal to one being passed, return amounts as 0,0" in {
        val bank1 = generateLiabilityBankDetails
        val dL1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(dateOfDisposal = Some(new LocalDate("2015-05-01")), periodKey = periodKey)), bankDetails = Some(bank1))
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(dL1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result = await(TestDisposeLiabilityReturnService.getPreCalculationAmounts(atedRefNo, formBundleReturn1, DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey), formBundle2))
        result must be(DisposeCalculated(BigDecimal(0.00), BigDecimal(0.00)))
      }
    }

    "deleteDisposeLiabilityDraft" must {
      "return Seq[DisposeLiabilityReturn] with removing the particular form-bundle-return, if form-bundle found in cache" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        val result = await(TestDisposeLiabilityReturnService.deleteDisposeLiabilityDraft(atedRefNo, formBundle1))
        result must be(Seq(disposeLiability2))
      }

      "return Nil if form-bundle not-found in cache" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo)))
          .thenReturn(Future.successful(Nil))
        val result = await(TestDisposeLiabilityReturnService.deleteDisposeLiabilityDraft(atedRefNo, formBundle1))
        result must be(Seq())
      }
    }

    "submitDisposeLiability" must {
      "return HttpResponse wit Status OK, when form-bundle is found in cache and successfully submitted to ETMP" must {
        "getEtmpBankDetails" must {
          "return BankDetails, if valid bank-details-model is passed" in {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetails), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
            when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
            when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
            lazy val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
            val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
          }
          "return None, if hasBankDetails is false passed" in {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsNoBankDetails), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
            when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
            when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
            val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            val respJson = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
            val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
          }

          "return None, if accountNumber & accountName & sortCode is not found" in {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey)), bankDetails = Some(ChangeLiabilityReturnBuilder.generateLiabilityProtectedBankDetailsBlank), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
            when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
            when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
            lazy val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
            val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
          }

          "return None, if None was passed as bank-details-model" in {
            lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey)), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
            when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
            when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
            when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
            when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
            when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
            lazy val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
            lazy val respJson = Json.toJson(respModel)
            when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
            val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
            result.status must be(OK)
            verify(mockEmailConnector, times(1)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
          }
        }
      }
      "generateEditReturnRequest - if dateOfDisposal is not found, use oldFormbundleReturn 'date from' value" in {
        lazy val bank1 = generateLiabilityBankDetails
        lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(None, periodKey)), bankDetails = Some(bank1), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        when(mockEmailConnector.sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())) thenReturn Future.successful(EmailSent)
        lazy val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        lazy val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(respJson))))
        val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(OK)
        verify(mockEmailConnector, times(1)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
      }
      "return NOT_FOUND as status, if form-bundle not found in list" in {
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disposeLiability2)))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(NOT_FOUND)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
      }
      "return the status with body, if etmp call returns any other status other than OK" in {
        lazy val bank1 = generateLiabilityBankDetails
        lazy val disp1 = disposeLiability1.copy(disposeLiability = Some(DisposeLiability(Some(new LocalDate("2015-05-01")), periodKey)), bankDetails = Some(bank1), calculated = Some(DisposeCalculated(BigDecimal(2500.00), BigDecimal(-500.00))))
        when(mockDisposeLiabilityReturnRepository.fetchDisposeLiabilityReturns(Matchers.eq(atedRefNo))).thenReturn(Future.successful(Seq(disp1, disposeLiability2)))
        when(mockDisposeLiabilityReturnRepository.cacheDisposeLiabilityReturns(Matchers.any[DisposeLiabilityReturn]())).thenReturn(Future.successful(DisposeLiabilityReturnCached))
        when(mockAuthConnector.agentReferenceNo(Matchers.any())).thenReturn(Future.successful(None))
        when(mockSubscriptionDataService.retrieveSubscriptionData(Matchers.any())).thenReturn(Future.successful(HttpResponse(OK, Some(successResponseJson))))
        lazy val respModel = generateEditLiabilityReturnResponse(formBundle1.toString)
        lazy val respJson = Json.toJson(respModel)
        when(mockEtmpConnector.submitEditedLiabilityReturns(Matchers.eq(atedRefNo), Matchers.any(), Matchers.any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(Json.parse("""{"reason": "Server error"}""")))))
        val result = await(TestDisposeLiabilityReturnService.submitDisposeLiability(atedRefNo, formBundle1))
        result.status must be(INTERNAL_SERVER_ERROR)
        verify(mockEmailConnector, times(0)).sendTemplatedEmail(Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())
      }
    }
  }

}
